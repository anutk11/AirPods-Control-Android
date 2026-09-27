package com.anutk11.airpodscontrol

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
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
    private var transport: ClassicL2capTransport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        log = TextView(this).apply {
            textSize = 14f
            setPadding(24, 24, 24, 24)
            text = "AirPods Control 0.2.0\n\nClassic L2CAP / AACP test\n"
        }

        val connect = Button(this).apply {
            text = "התחבר ב-Classic L2CAP"
            setOnClickListener { connectToAirPods() }
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

    private fun connectToAirPods() {
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
        if (adapter == null || !adapter.isEnabled) {
            appendLog("ERROR: Bluetooth אינו זמין או כבוי")
            return
        }

        val devices = adapter.bondedDevices.toList()
        if (devices.isEmpty()) {
            appendLog("ERROR: אין התקן Bluetooth מצומד")
            return
        }

        val candidates = devices.filter { isAirPodsName(it.name) }
        val device = candidates.firstOrNull() ?: devices.firstOrNull()
        if (device == null) {
            appendLog("ERROR: לא נמצא התקן")
            return
        }

        appendLog("Target: ${device.name ?: "Unknown"} — ${device.address}")
        appendLog("Pairing state: ${device.bondState}")
        appendLog("Opening Classic L2CAP (0x1001)")

        Thread {
            val t = ClassicL2capTransport(device) { message -> runOnUiThread { appendLog(message) } }
            transport = t
            t.connectAndHandshake()
        }.start()
    }

    private fun isAirPodsName(name: String?): Boolean {
        val n = name?.lowercase() ?: return false
        return n.contains("airpods") || n.contains("airpod") || n.contains("pods pro")
    }

    private fun appendLog(message: String) {
        log.append("$message\n")
    }

    override fun onDestroy() {
        transport?.close()
        super.onDestroy()
    }
}
