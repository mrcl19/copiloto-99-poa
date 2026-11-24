package com.copiloto.poa99

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatusOverlay: TextView
    private lateinit var tvStatusAccessibility: TextView
    private lateinit var swAutoSchedule: Switch
    private lateinit var swBlockZonaSul: Switch
    private lateinit var swBlockCashNight: Switch
    private lateinit var etMinKm: EditText
    private lateinit var etMinMinute: EditText
    private lateinit var etMaxPickup: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatusOverlay = findViewById(R.id.tvStatusOverlay)
        tvStatusAccessibility = findViewById(R.id.tvStatusAccessibility)
        swAutoSchedule = findViewById(R.id.swAutoSchedule)
        swBlockZonaSul = findViewById(R.id.swBlockZonaSul)
        swBlockCashNight = findViewById(R.id.swBlockCashNight)
        etMinKm = findViewById(R.id.etMinKm)
        etMinMinute = findViewById(R.id.etMinMinute)
        etMaxPickup = findViewById(R.id.etMaxPickup)

        loadSettings()

        findViewById<Button>(R.id.btnPermOverlay).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnPermAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Autorize primeiro a Janela Flutuante!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Copiloto ativo em segundo plano!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnTestOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Autorize a Janela Flutuante primeiro!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val testIntent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW_OFFER
                putExtra(OverlayService.EXTRA_FARE, 22.50)
                putExtra(OverlayService.EXTRA_PICKUP_KM, 1.1)
                putExtra(OverlayService.EXTRA_TRIP_KM, 7.2)
                putExtra(OverlayService.EXTRA_TOTAL_KM, 8.3)
                putExtra(OverlayService.EXTRA_TOTAL_MIN, 21)
                putExtra(OverlayService.EXTRA_RATING, 4.94)
                putExtra(OverlayService.EXTRA_IS_CASH, true)
                putExtra(OverlayService.EXTRA_IS_NEW, false)
                putExtra(OverlayService.EXTRA_DEST_TEXT, "Moinhos de Vento, Porto Alegre")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(testIntent)
            } else {
                startService(testIntent)
            }
        }

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            saveSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun updatePermissionStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        tvStatusOverlay.text = if (overlayOk) {
            "Sobreposição de Tela: ✅ AUTORIZADA"
        } else {
            "Sobreposição de Tela: ❌ PENDENTE"
        }

        val accOk = isAccessibilityServiceEnabled()
        tvStatusAccessibility.text = if (accOk) {
            "Leitura Automática (99): ✅ ATIVA"
        } else {
            "Leitura Automática (99): ❌ DESATIVADA"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "$packageName/${RideAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedService, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("copiloto_prefs", Context.MODE_PRIVATE)
        swAutoSchedule.isChecked = prefs.getBoolean("auto_schedule", true)
        swBlockZonaSul.isChecked = prefs.getBoolean("block_zona_sul", true)
        swBlockCashNight.isChecked = prefs.getBoolean("block_cash_night", true)
        etMinKm.setText(prefs.getFloat("min_km", 2.30f).toString())
        etMinMinute.setText(prefs.getFloat("min_minute", 0.75f).toString())
        etMaxPickup.setText(prefs.getFloat("max_pickup", 1.5f).toString())
    }

    private fun saveSettings() {
        val minKm = etMinKm.text.toString().replace(",", ".").toFloatOrNull() ?: 2.30f
        val minMinute = etMinMinute.text.toString().replace(",", ".").toFloatOrNull() ?: 0.75f
        val maxPickup = etMaxPickup.text.toString().replace(",", ".").toFloatOrNull() ?: 1.5f

        getSharedPreferences("copiloto_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("auto_schedule", swAutoSchedule.isChecked)
            .putBoolean("block_zona_sul", swBlockZonaSul.isChecked)
            .putBoolean("block_cash_night", swBlockCashNight.isChecked)
            .putFloat("min_km", minKm)
            .putFloat("min_minute", minMinute)
            .putFloat("max_pickup", maxPickup)
            .apply()

        Toast.makeText(this, "Filtros salvos com sucesso!", Toast.LENGTH_SHORT).show()
    }
}
