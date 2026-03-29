package com.trackpad.cursor
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
    override fun onResume() {
        super.onResume()
        val enabled = isServiceEnabled()
        val statusView = findViewById<TextView>(R.id.tvStatus)
        val btn = findViewById<Button>(R.id.btnAccessibility)
        if (enabled) {
            statusView.text = "✅ Service running! Hold Vol Down 3 sec to toggle."
            statusView.setTextColor(0xFF4CAF50.toInt())
            btn.text = "Open Accessibility Settings"
        } else {
            statusView.text = "❌ Not enabled. Tap below to enable."
            statusView.setTextColor(0xFFFF7043.toInt())
            btn.text = "Enable Accessibility Service →"
        }
    }
    private fun isServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val target = "$packageName/${TrackpadService::class.java.name}"
        return flat.split(":").any { it.equals(target, ignoreCase = true) }
    }
}
