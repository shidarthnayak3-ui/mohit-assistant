package com.mohit.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48) }
        fun btn(t: String, f: () -> Unit) {
            l.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        }
        l.addView(TextView(this).apply { text = "मोहित असिस्टेंट\nबोलिए: मोहित, व्हाट्सएप खोलो"; textSize = 20f })
        btn("1. अनुमतियाँ दें") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE), 1)
        }
        btn("2. ऐप जानकारी (Allow restricted settings)") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
        }
        btn("3. एक्सेसिबिलिटी चालू करें") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("4. बैटरी: Unrestricted करें") { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        btn("5. मोहित को चालू करें") { startForegroundService(Intent(this, VoiceService::class.java)) }
        btn("बंद करें") { stopService(Intent(this, VoiceService::class.java)) }
        setContentView(ScrollView(this).apply { addView(l) })
    }
}
