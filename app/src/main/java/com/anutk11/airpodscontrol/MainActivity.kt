package com.anutk11.airpodscontrol

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        log = TextView(this).apply {
            textSize = 14f
            setPadding(24, 24, 24, 24)
            text = "AirPods Control 0.1.0\n\nשלב 1: Classic L2CAP / AACP\n"
        }

        val connect = Button(this).apply {
            text = "בדוק Bluetooth והתקנים מצומדים"
            setOnClickListener { inspectBluetooth() }
        }
        val clear = Button(this).apply {
            text = "נקה"
            setOnClickListener { log.text = "" }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(connect)
            addView(clear)
            addView(ScrollView(this@MainActivity).apply { addView(log) }, LinearLayout.LayoutParams(-1, 0, 1f))
        })
    }

    private fun inspectBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                100
            )
            return
        }

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter
        if (adapter == null) {
            appendLog("ERROR: Bluetooth adapter אינו זמין")
            return
        }
        if (!adapter.isEnabled) {
            appendLog("ERROR: Bluetooth כבוי")
            return
        }

        val devices = adapter.bondedDevices
        appendLog("Bluetooth פעיל. התקנים מצומדים: ${devices.size}")
        devices.forEach { device ->
            appendLog("• ${device.name ?: "Unknown"} — ${device.address}")
        }
        appendLog("שלב הבא: Classic L2CAP PSM 0x1001")
    }

    private fun appendLog(message: String) {
        log.append("$message\n")
    }
}
