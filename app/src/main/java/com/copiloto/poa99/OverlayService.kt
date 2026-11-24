package com.copiloto.poa99

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { removeOverlay() }

    companion object {
        const val ACTION_SHOW_OFFER = "com.copiloto.poa99.SHOW_OFFER"
        const val EXTRA_FARE = "fare"
        const val EXTRA_PICKUP_KM = "pickup_km"
        const val EXTRA_TRIP_KM = "trip_km"
        const val EXTRA_TOTAL_KM = "total_km"
        const val EXTRA_TOTAL_MIN = "total_min"
        const val EXTRA_RATING = "rating"
        const val EXTRA_IS_CASH = "is_cash"
        const val EXTRA_IS_NEW = "is_new"
        const val EXTRA_DEST_TEXT = "dest_text"
        private const val CHANNEL_ID = "copiloto_99_channel"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Copiloto 99 POA Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Copiloto 99 POA Ativo")
            .setContentText("Aguardando chamadas da 99 para calcular R$/km e R$/min...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_OFFER) {
            val offer = RideOffer(
                fare = intent.getDoubleExtra(EXTRA_FARE, 0.0),
                pickupKm = intent.getDoubleExtra(EXTRA_PICKUP_KM, 0.0),
                tripKm = intent.getDoubleExtra(EXTRA_TRIP_KM, 0.0),
                totalKm = intent.getDoubleExtra(EXTRA_TOTAL_KM, 0.0),
                totalMin = intent.getIntExtra(EXTRA_TOTAL_MIN, 0),
                rating = intent.getDoubleExtra(EXTRA_RATING, -1.0).let { if (it > 0) it else null },
                isCash = intent.getBooleanExtra(EXTRA_IS_CASH, false),
                isNewAccount = intent.getBooleanExtra(EXTRA_IS_NEW, false),
                destinationText = intent.getStringExtra(EXTRA_DEST_TEXT) ?: ""
            )
            if (offer.fare > 0.0) {
                val verdict = RideDecisionEngine.evaluate(this, offer)
                showOverlayCard(verdict)
            }
        }
        return START_STICKY
    }

    private fun showOverlayCard(verdict: RideVerdict) {
        if (!Settings.canDrawOverlays(this)) return

        mainHandler.removeCallbacks(hideRunnable)
        removeOverlay()

        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_ride_card, null)

        val tvPhase = view.findViewById<TextView>(R.id.tvPhaseTitle)
        val bannerVerdict = view.findViewById<LinearLayout>(R.id.bannerVerdict)
        val tvVerdictTitle = view.findViewById<TextView>(R.id.tvVerdictTitle)
        val tvVerdictReason = view.findViewById<TextView>(R.id.tvVerdictReason)
        val tvMetricKm = view.findViewById<TextView>(R.id.tvMetricKm)
        val tvMetricMin = view.findViewById<TextView>(R.id.tvMetricMin)
        val tvMetricHour = view.findViewById<TextView>(R.id.tvMetricHour)
        val tvSummary = view.findViewById<TextView>(R.id.tvRideSummary)
        val btnClose = view.findViewById<TextView>(R.id.btnCloseOverlay)

        tvPhase.text = "COPILOTO 99 POA • ${verdict.phaseName}"

        if (verdict.accept) {
            bannerVerdict.setBackgroundResource(R.drawable.bg_badge_accept)
            tvVerdictTitle.text = "✅ ACEITAR CORRIDA"
        } else {
            bannerVerdict.setBackgroundResource(R.drawable.bg_badge_reject)
            tvVerdictTitle.text = "⛔ RECUSAR CORRIDA"
        }

        tvVerdictReason.text = verdict.reason
        tvMetricKm.text = String.format(Locale.US, "R$ %.2f", verdict.rPerKm).replace(".", ",")
        tvMetricMin.text = String.format(Locale.US, "R$ %.2f", verdict.rPerMin).replace(".", ",")
        tvMetricHour.text = String.format(Locale.US, "R$ %.0f/h", verdict.rPerHour)

        val pgtoLabel = if (verdict.offer.isCash) "Dinheiro" else "App/Cartão"
        val ratingLabel = verdict.offer.rating?.let {
            String.format(Locale.US, " • ★ %.2f", it).replace(".", ",")
        } ?: ""
        val pickupLabel = if (verdict.offer.pickupKm > 0) {
            String.format(Locale.US, " (busca %.1f km)", verdict.offer.pickupKm).replace(".", ",")
        } else ""

        tvSummary.text = String.format(
            Locale.US,
            "Valor: R$ %.2f • Total: %.1f km%s • %d min • %s%s",
            verdict.offer.fare,
            verdict.offer.totalKm,
            pickupLabel,
            verdict.offer.totalMin,
            pgtoLabel,
            ratingLabel
        ).replace(".", ",")

        btnClose.setOnClickListener { removeOverlay() }
        view.setOnClickListener { removeOverlay() }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 70
        }

        overlayView = view
        windowManager?.addView(view, params)
        mainHandler.postDelayed(hideRunnable, 9500)
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
            overlayView = null
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(hideRunnable)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
