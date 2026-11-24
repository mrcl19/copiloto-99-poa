package com.copiloto.poa99

import java.util.Locale
import java.util.regex.Pattern

object RideParser {

    private val FARE_PATTERN = Pattern.compile("R\$\s*(\d+[.,]\d{2})")
    private val KM_PATTERN = Pattern.compile("(\d+[.,]?\d*)\s*km", Pattern.CASE_INSENSITIVE)
    private val METER_PATTERN = Pattern.compile("(\d+)\s*m\b", Pattern.CASE_INSENSITIVE)
    private val MIN_PATTERN = Pattern.compile("(\d+)\s*min", Pattern.CASE_INSENSITIVE)
    private val RATING_PATTERN = Pattern.compile("\b([45][.,]\d{2})\b")

    fun parseFromTexts(texts: List<String>): RideOffer? {
        if (texts.isEmpty()) return null
        val fullText = texts.joinToString(" | ")
        val lowerText = fullText.lowercase(Locale.getDefault())

        // Verifica se a tela parece ser um card de oferta de corrida
        val hasOfferKeywords = lowerText.contains("aceitar") ||
                lowerText.contains("recusar") ||
                lowerText.contains("min") && lowerText.contains("km")
        if (!hasOfferKeywords) return null

        // 1. Extrai valor da corrida (R$)
        val fareMatcher = FARE_PATTERN.matcher(fullText)
        var fare = 0.0
        while (fareMatcher.find()) {
            val valStr = fareMatcher.group(1)?.replace(",", ".") ?: continue
            val parsed = valStr.toDoubleOrNull() ?: 0.0
            if (parsed > fare && parsed < 500.0) {
                fare = parsed
            }
        }
        if (fare <= 0.0) return null

        // 2. Extrai distâncias (km e metros)
        val distancesKm = mutableListOf<Double>()
        val kmMatcher = KM_PATTERN.matcher(fullText)
        while (kmMatcher.find()) {
            val dStr = kmMatcher.group(1)?.replace(",", ".") ?: continue
            dStr.toDoubleOrNull()?.let { if (it in 0.1..150.0) distancesKm.add(it) }
        }
        val meterMatcher = METER_PATTERN.matcher(fullText)
        while (meterMatcher.find()) {
            val mStr = meterMatcher.group(1) ?: continue
            mStr.toDoubleOrNull()?.let { if (it in 50.0..999.0) distancesKm.add(it / 1000.0) }
        }

        val pickupKm: Double
        val tripKm: Double
        val totalKm: Double
        when {
            distancesKm.size >= 2 -> {
                pickupKm = distancesKm[0]
                tripKm = distancesKm[1]
                totalKm = pickupKm + tripKm
            }
            distancesKm.size == 1 -> {
                pickupKm = 0.0
                tripKm = distancesKm[0]
                totalKm = distancesKm[0]
            }
            else -> return null
        }

        // 3. Extrai tempos (minutos)
        val durationsMin = mutableListOf<Int>()
        val minMatcher = MIN_PATTERN.matcher(fullText)
        while (minMatcher.find()) {
            val tStr = minMatcher.group(1) ?: continue
            tStr.toIntOrNull()?.let { if (it in 1..180) durationsMin.add(it) }
        }

        val totalMin = when {
            durationsMin.size >= 2 -> durationsMin[0] + durationsMin[1]
            durationsMin.size == 1 -> durationsMin[0]
            else -> (totalKm * 2.2).toInt().coerceAtLeast(3)
        }

        // 4. Extrai nota do passageiro
        var rating: Double? = null
        val ratingMatcher = RATING_PATTERN.matcher(fullText)
        if (ratingMatcher.find()) {
            rating = ratingMatcher.group(1)?.replace(",", ".")?.toDoubleOrNull()
        }

        // 5. Forma de pagamento e conta nova
        val isCash = lowerText.contains("dinheiro") || lowerText.contains("espécie") || lowerText.contains("especie")
        val isNewAccount = lowerText.contains("novo") || lowerText.contains("nova") || lowerText.contains("primeira viagem")

        return RideOffer(
            fare = fare,
            pickupKm = pickupKm,
            tripKm = tripKm,
            totalKm = totalKm,
            totalMin = totalMin,
            rating = rating,
            isCash = isCash,
            isNewAccount = isNewAccount,
            destinationText = fullText
        )
    }
}
