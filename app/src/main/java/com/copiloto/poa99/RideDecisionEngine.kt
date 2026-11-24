package com.copiloto.poa99

import android.content.Context
import java.util.Calendar
import java.util.Locale

data class RideOffer(
    val fare: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val totalKm: Double,
    val totalMin: Int,
    val rating: Double?,
    val isCash: Boolean,
    val isNewAccount: Boolean,
    val destinationText: String
)

data class RideVerdict(
    val accept: Boolean,
    val phaseName: String,
    val reason: String,
    val rPerKm: Double,
    val rPerMin: Double,
    val rPerHour: Double,
    val offer: RideOffer
)

object RideDecisionEngine {

    private val BLOCKED_DESTINATIONS_POST_15H = listOf(
        "cavalhada", "tristeza", "ipanema", "restinga",
        "viamão", "viamao", "belém novo", "belem novo",
        "ponta grossa", "lami", "hípica", "hipica",
        "campo novo", "gravataí", "gravatai", "cachoeirinha",
        "canoas", "alvorada"
    )

    fun evaluate(context: Context, offer: RideOffer): RideVerdict {
        val prefs = context.getSharedPreferences("copiloto_prefs", Context.MODE_PRIVATE)
        val autoSchedule = prefs.getBoolean("auto_schedule", true)
        val blockZonaSul = prefs.getBoolean("block_zona_sul", true)
        val blockCashNight = prefs.getBoolean("block_cash_night", true)
        val customMinKm = prefs.getFloat("min_km", 2.30f).toDouble()
        val minPerMin = prefs.getFloat("min_minute", 0.75f).toDouble()
        val maxPickupKm = prefs.getFloat("max_pickup", 1.5f).toDouble()

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val timeInMinutes = hour * 60 + minute

        val safeKm = if (offer.totalKm > 0.1) offer.totalKm else 1.0
        val safeMin = if (offer.totalMin > 0) offer.totalMin else 1

        val rPerKm = offer.fare / safeKm
        val rPerMin = offer.fare / safeMin
        val rPerHour = rPerMin * 60.0

        val (phaseName, scheduleMinKm) = getPhaseAndTargetKm(timeInMinutes, customMinKm)
        val effectiveMinKm = if (autoSchedule) scheduleMinKm else customMinKm

        // 1. Trava do compromisso fixo às 19:00 na Santa Casa
        if (timeInMinutes >= 18 * 60 + 30) {
            return RideVerdict(
                accept = false,
                phaseName = phaseName,
                reason = "Encerramento 18h30: Siga para a Santa Casa!",
                rPerKm = rPerKm,
                rPerMin = rPerMin,
                rPerHour = rPerHour,
                offer = offer
            )
        }

        // 2. Trava pós-15h para Zona Sul e extremos
        if (blockZonaSul && timeInMinutes >= 15 * 60) {
            val destLower = offer.destinationText.lowercase(Locale.getDefault())
            val blockedMatch = BLOCKED_DESTINATIONS_POST_15H.firstOrNull { destLower.contains(it) }
            if (blockedMatch != null) {
                return RideVerdict(
                    accept = false,
                    phaseName = phaseName,
                    reason = "Trava pós-15h (${blockedMatch.uppercase()}): Risco de atraso 19h",
                    rPerKm = rPerKm,
                    rPerMin = rPerMin,
                    rPerHour = rPerHour,
                    offer = offer
                )
            }
        }

        // 3. Travas de segurança para pagamento em Dinheiro
        if (offer.isCash) {
            if (blockCashNight && (timeInMinutes < 7 * 60 || timeInMinutes >= 17 * 60 + 30)) {
                return RideVerdict(
                    accept = false,
                    phaseName = phaseName,
                    reason = "Trava Segurança: Dinheiro desligado neste horário",
                    rPerKm = rPerKm,
                    rPerMin = rPerMin,
                    rPerHour = rPerHour,
                    offer = offer
                )
            }
            if (offer.isNewAccount) {
                return RideVerdict(
                    accept = false,
                    phaseName = phaseName,
                    reason = "Conta nova no dinheiro: alto risco",
                    rPerKm = rPerKm,
                    rPerMin = rPerMin,
                    rPerHour = rPerHour,
                    offer = offer
                )
            }
            if (offer.rating != null && offer.rating < 4.85) {
                return RideVerdict(
                    accept = false,
                    phaseName = phaseName,
                    reason = String.format(Locale.US, "Nota %.2f abaixo de 4,85 no dinheiro", offer.rating),
                    rPerKm = rPerKm,
                    rPerMin = rPerMin,
                    rPerHour = rPerHour,
                    offer = offer
                )
            }
        }

        // 4. Filtro de distância de busca até o passageiro
        if (offer.pickupKm > 0.0 && offer.pickupKm > maxPickupKm) {
            return RideVerdict(
                accept = false,
                phaseName = phaseName,
                reason = String.format(Locale.US, "Busca longa: %.1f km (máx %.1f km)", offer.pickupKm, maxPickupKm),
                rPerKm = rPerKm,
                rPerMin = rPerMin,
                rPerHour = rPerHour,
                offer = offer
            )
        }

        // 5. Filtro de rentabilidade por KM
        if (rPerKm < effectiveMinKm) {
            return RideVerdict(
                accept = false,
                phaseName = phaseName,
                reason = String.format(Locale.US, "KM baixo: R$ %.2f/km (mín R$ %.2f)", rPerKm, effectiveMinKm),
                rPerKm = rPerKm,
                rPerMin = rPerMin,
                rPerHour = rPerHour,
                offer = offer
            )
        }

        // 6. Filtro de rentabilidade por Minuto (evita trânsito travado)
        if (rPerMin < minPerMin) {
            return RideVerdict(
                accept = false,
                phaseName = phaseName,
                reason = String.format(Locale.US, "Trânsito lento: R$ %.2f/min (mín R$ %.2f)", rPerMin, minPerMin),
                rPerKm = rPerKm,
                rPerMin = rPerMin,
                rPerHour = rPerHour,
                offer = offer
            )
        }

        return RideVerdict(
            accept = true,
            phaseName = phaseName,
            reason = String.format(Locale.US, "Meta batida! (Alvo da faixa: R$ %.2f/km)", effectiveMinKm),
            rPerKm = rPerKm,
            rPerMin = rPerMin,
            rPerHour = rPerHour,
            offer = offer
        )
    }

    private fun getPhaseAndTargetKm(timeInMinutes: Int, fallbackKm: Double): Pair<String, Double> {
        return when {
            timeInMinutes in (5 * 60 + 30) until (7 * 60) -> "DESCIDA SL → POA" to 1.80
            timeInMinutes in (7 * 60) until (10 * 60 + 30) -> "PICO MANHÃ FULL" to 2.60
            timeInMinutes in (10 * 60 + 30) until (11 * 60 + 30) -> "GIRO DE VALE" to 2.10
            timeInMinutes in (11 * 60 + 30) until (14 * 60 + 15) -> "GIRO EXECUTIVO PLUS" to 2.30
            timeInMinutes in (14 * 60 + 15) until (15 * 60) -> "PIT STOP (PAUSA)" to 2.40
            timeInMinutes in (15 * 60) until (17 * 60 + 30) -> "SPRINT DA TARDE" to 2.50
            timeInMinutes in (17 * 60 + 30) until (18 * 60 + 30) -> "CERCO FINAL SANTA CASA" to 2.20
            else -> "MODO TURBO POA" to fallbackKm
        }
    }
}
