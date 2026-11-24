package com.copiloto.poa99

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class CopilotoAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var statusText: TextView? = null
    private var metricsText: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastSignature = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        showPersistentCard()
    }

    private fun showPersistentCard() {
        if (overlayView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 18, 28, 18)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EB121212"))
                cornerRadius = 24f
                setStroke(4, Color.parseColor("#3B82F6"))
            }
        }

        val title = TextView(this).apply {
            text = "COPILOTO 99 • PRONTO"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
        }

        val metrics = TextView(this).apply {
            text = "Aguardando chamada..."
            setTextColor(Color.parseColor("#D1D5DB"))
            textSize = 12f
        }

        container.addView(title)
        container.addView(metrics)
        statusText = title
        metricsText = metrics

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 130
        }

        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x
                    initY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (event.rawX - touchX).toInt()
                    params.y = initY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }

        overlayView = container
        windowManager?.addView(container, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val allTexts = mutableListOf<String>()

        try {
            windows?.forEach { window ->
                window.root?.let { collectTexts(it, allTexts) }
            }
        } catch (_: Exception) { }

        if (allTexts.isEmpty()) {
            rootInActiveWindow?.let { collectTexts(it, allTexts) }
        }

        if (allTexts.isEmpty()) return

        val offer = RideParser.parseFromTexts(allTexts) ?: return
        val signature = "${offer.fare}-${offer.totalKm}-${offer.totalMin}"
        if (signature == lastSignature) return
        lastSignature = signature

        val rKm = if (offer.totalKm > 0) offer.fare / offer.totalKm else 0.0
        val rMin = if (offer.totalMin > 0) offer.fare / offer.totalMin else 0.0

        val (verdict, bgHex, borderHex) = when {
            offer.isCash -> Triple("RECUSAR • DINHEIRO", "#EB7F1D1D", "#EF4444")
            rKm >= 2.30 && rMin >= 0.75 -> Triple("ACEITAR • BOA", "#EB14532D", "#22C55E")
            rKm >= 1.90 -> Triple("ATENÇÃO • MÉDIA", "#EB713F12", "#EAB308")
            else -> Triple("RECUSAR • BAIXA", "#EB7F1D1D", "#EF4444")
        }

        val details = String.format(
            Locale.US,
            "R$ %.2f | %.1f km | R$ %.2f/km | R$ %.2f/min",
            offer.fare, offer.totalKm, rKm, rMin
        )

        mainHandler.post {
            statusText?.text = verdict
            metricsText?.text = details
            (overlayView?.background as? GradientDrawable)?.apply {
                setColor(Color.parseColor(bgHex))
                setStroke(4, Color.parseColor(borderHex))
            }
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { list.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { list.add(it) }
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), list)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        super.onDestroy()
    }
}
