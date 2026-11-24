package com.copiloto.poa99

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class RideAccessibilityService : AccessibilityService() {

    private var lastOfferSignature: String = ""
    private var lastTriggerTimeMs: Long = 0L

    private val targetPackages = setOf(
        "com.taxis99.driver",
        "com.didiglobal.driver",
        "com.ubercab.driver",
        "sinet.startup.inDriver"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkgName = event?.packageName?.toString() ?: return
        if (!targetPackages.contains(pkgName)) return

        val rootNode = rootInActiveWindow ?: return
        val collectedTexts = mutableListOf<String>()
        collectNodeTexts(rootNode, collectedTexts)

        val offer = RideParser.parseFromTexts(collectedTexts) ?: return
        val signature = "${offer.fare}_${offer.totalKm}_${offer.totalMin}_${offer.isCash}"
        val now = System.currentTimeMillis()

        // Evita disparar repetidamente para o mesmo card na tela
        if (signature == lastOfferSignature && (now - lastTriggerTimeMs) < 12000L) {
            return
        }

        lastOfferSignature = signature
        lastTriggerTimeMs = now

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_OFFER
            putExtra(OverlayService.EXTRA_FARE, offer.fare)
            putExtra(OverlayService.EXTRA_PICKUP_KM, offer.pickupKm)
            putExtra(OverlayService.EXTRA_TRIP_KM, offer.tripKm)
            putExtra(OverlayService.EXTRA_TOTAL_KM, offer.totalKm)
            putExtra(OverlayService.EXTRA_TOTAL_MIN, offer.totalMin)
            putExtra(OverlayService.EXTRA_RATING, offer.rating ?: -1.0)
            putExtra(OverlayService.EXTRA_IS_CASH, offer.isCash)
            putExtra(OverlayService.EXTRA_IS_NEW, offer.isNewAccount)
            putExtra(OverlayService.EXTRA_DEST_TEXT, offer.destinationText)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun collectNodeTexts(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }

        for (i in 0 until node.childCount) {
            collectNodeTexts(node.getChild(i), out)
        }
    }

    override fun onInterrupt() {
        // Interrupção do serviço
    }
}
