package com.anutk11.airpodscontrol

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val log = TextView(this).apply { text = "AirPods Control 0.1.0\n\nשלב 1: Classic L2CAP / AACP" }
        val button = Button(this).apply { text = "התחבר ב-Classic L2CAP" }
        val clear = Button(this).apply { text = "נקה"; setOnClickListener { log.text = "" } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(button)
            addView(clear)
            addView(log)
        })
    }
}
