package com.mohit.assistant

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.*
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceService : Service(), TextToSpeech.OnInitListener {
    private var rec: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private val h = Handler(Looper.getMainLooper())
    private var speaking = false
    private var awaiting = false
    private var alive = true
    private val wake = listOf("mohit", "मोहित", "मोहीत", "mohith", "mohit,")

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return
        }
        val ch = NotificationChannel("voice", "Mohit", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "voice").setContentTitle("मोहित सुन रहा है")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
        tts = TextToSpeech(this, this)
    }

    override fun onInit(s: Int) {
        tts.language = Locale("hi", "IN")
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) { speaking = false; h.post { listen() } }
            override fun onDone(id: String?) { speaking = false; h.post { listen() } }
        })
        speak("मोहित तैयार है")
    }

    private fun speak(t: String) {
        speaking = true
        rec?.cancel()
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u" + System.currentTimeMillis())
    }

    private fun listen() {
        if (!alive) return
        if (speaking) return
        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(this)
        rec?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t != null) handle(t.lowercase())
                if (!speaking) h.postDelayed({ listen() }, 300)
            }
            override fun onError(e: Int) { if (!speaking) h.postDelayed({ listen() }, 700) }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
        rec?.startListening(i)
    }

    private fun handle(text: String) {
        var cmd = text
        val w = wake.firstOrNull { text.contains(it) }
        if (w != null) {
            cmd = text.substringAfter(w).trim().trimStart(',', ' ')
            if (cmd.isEmpty()) { awaiting = true; speak("जी, बोलिए"); return }
        } else if (!awaiting) return
        awaiting = false
        run(cmd)
    }

    private fun launch(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { (ControlService.inst ?: this as android.content.Context).startActivity(i) } catch (e: Exception) { speak("नहीं खुल पाया") }
    }

    private fun has(c: String, vararg k: String) = k.any { c.contains(it) }

    private fun run(c: String) {
        val acc = ControlService.inst
        when {
            has(c, "वापस", "पीछे", "back") -> { acc?.back(); speak("ठीक है") }
            has(c, "होम", "home") -> { acc?.home(); speak("ठीक है") }
            has(c, "रीसेंट", "recent") -> { acc?.recents(); speak("ठीक है") }
            has(c, "नीचे", "down") -> { acc?.scroll(true) }
            has(c, "ऊपर", "up") -> { acc?.scroll(false) }
            has(c, "टैप", "दबाओ", "tap") -> { acc?.tapCenter() }
            has(c, "अलार्म", "alarm") -> setAlarm(c)
            has(c, "कॉल", "फोन करो", "call") -> callName(c)
            has(c, "खोलो", "खोल", "चालू", "open", "kholo") -> openApp(c)
            else -> speak("समझ नहीं आया")
        }
        if (acc == null && has(c, "वापस", "पीछे", "होम", "नीचे", "ऊपर", "टैप", "रीसेंट", "back", "home", "down", "up"))
            speak("पहले एक्सेसिबिलिटी चालू करें")
    }

    private fun setAlarm(c: String) {
        val m = Regex("\\d+").find(c)
        var hr = m?.value?.toIntOrNull()
        if (hr == null) { speak("कितने बजे का अलार्म?"); return }
        if (has(c, "शाम", "रात", "pm") && hr < 12) hr += 12
        val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hr % 24)
            .putExtra(AlarmClock.EXTRA_MINUTES, 0).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        launch(i); speak("अलार्म लगा दिया")
    }

    private fun callName(c: String) {
        val name = c.replace(Regex("कॉल|करो|फोन|मोहित|call|को|\\s+"), " ").trim()
        if (name.isEmpty()) { speak("किसे कॉल करूँ?"); return }
        var num: String? = null
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"), null)?.use {
                if (it.moveToFirst()) num = it.getString(0)
            }
        }
        if (num == null) { speak("$name नहीं मिला"); return }
        speak("$name को कॉल कर रहा हूँ")
        launch(Intent(if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED)
            Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$num")))
    }

    private val alias = mapOf("व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वॉट्सऐप" to "whatsapp",
        "व्हाट्स ऐप" to "whatsapp", "यूट्यूब" to "youtube", "यू ट्यूब" to "youtube", "क्रोम" to "chrome",
        "कैमरा" to "camera", "गैलरी" to "gallery", "सेटिंग" to "settings", "सेटिंग्स" to "settings",
        "फेसबुक" to "facebook", "इंस्टाग्राम" to "instagram", "गूगल" to "google", "मैप" to "maps",
        "फोनपे" to "phonepe", "पेटीएम" to "paytm", "कैलकुलेटर" to "calculator", "घड़ी" to "clock")

    private fun openApp(c: String) {
        var n = c.replace(Regex("खोलो|खोल|चालू|करो|open|kholo|मोहित|को|दो"), " ").trim()
        for ((k, v) in alias) if (n.contains(k)) n = n.replace(k, v)
        n = n.trim()
        if (n.isEmpty()) { speak("कौन सा ऐप?"); return }
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(q, 0)
        val hit = list.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) }
            ?: list.firstOrNull { n.contains(it.loadLabel(pm).toString().lowercase()) }
        val pkg = hit?.activityInfo?.packageName
        val li = pkg?.let { pm.getLaunchIntentForPackage(it) }
        if (li == null) { speak("$n नहीं मिला"); return }
        speak("$n खोल रहा हूँ"); launch(li)
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int = START_STICKY

    override fun onDestroy() {
        alive = false
        rec?.destroy()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
