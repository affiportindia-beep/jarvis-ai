package com.jarvisx.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import com.example.ava.wakewords.microwakeword.MicroWakeWordDetector
import com.example.ava.wakewords.microwakeword.MicroWakeWordModel
import com.jarvisx.app.JarvisApp
import com.jarvisx.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * "Hey Jarvis": listens for the wake word with a 52 KB on-device model (microWakeWord).
 *
 * Runs only when the user switched it on, as a foreground service with a permanent notification,
 * so Android shows the microphone indicator the whole time. No audio leaves the phone and none
 * is stored. After the wake word, the on-device recogniser takes the question, JARVIS X answers
 * out loud, and wake-word listening resumes. Listening pauses while JARVIS X itself is speaking.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as JarvisApp).graph.settings.setWakeWord(false)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (loop?.isActive != true) loop = scope.launch { runDetection() }
        return START_STICKY
    }

    @SuppressLint("MissingPermission") // checked in start() and below
    private suspend fun runDetection() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _status.value = "Microphone permission missing"
            stopSelf()
            return
        }
        val graph = (application as JarvisApp).graph
        val detector = try {
            MicroWakeWordDetector(listOf(loadModel()))
        } catch (e: Throwable) {
            Log.e(TAG, "wake model failed", e)
            _status.value = "Wake word could not start: ${e.message}"
            stopSelf()
            return
        }
        val bytesPerRead = SAMPLE_RATE / 20 * 2 // 50 ms of 16-bit audio
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val buffer = ByteBuffer.allocateDirect(bytesPerRead).order(ByteOrder.LITTLE_ENDIAN)
        try {
            while (scope.isActive) {
                // Do not listen while JARVIS X is busy, speaking, or taking a question.
                if (graph.session.busy.value || graph.voiceOutput.speaking.value ||
                    graph.voiceInput.state.value is ListenState.Listening || graph.voiceInput.state.value is ListenState.Partial
                ) {
                    _status.value = "Paused while JARVIS X is talking"
                    delay(300)
                    continue
                }
                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, bytesPerRead * 4),
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    _status.value = "Microphone busy (another app may be using it)"
                    delay(2000)
                    continue
                }
                var heard = false
                record.startRecording()
                _status.value = "Listening for \"Hey Jarvis\""
                try {
                    while (scope.isActive && !heard) {
                        if (graph.session.busy.value || graph.voiceOutput.speaking.value) break
                        buffer.clear()
                        val n = record.read(buffer, bytesPerRead)
                        if (n <= 0) { delay(20); continue }
                        buffer.limit(n)
                        buffer.position(0)
                        heard = detector.detect(buffer).toList().isNotEmpty()
                    }
                } finally {
                    runCatching { record.stop() }
                    record.release() // free the microphone before the recogniser needs it
                }
                if (heard) {
                    detector.reset()
                    _status.value = "Heard \"Hey Jarvis\""
                    onWake(graph)
                }
            }
        } finally {
            detector.close()
        }
    }

    /** Takes one question with the on-device recogniser and answers out loud. */
    private suspend fun onWake(graph: com.jarvisx.app.AppGraph) {
        withContext(Dispatchers.Main) {
            graph.voiceOutput.speak("Ji?", com.jarvisx.core.lang.ReplyLanguage.ENGLISH)
        }
        // Wait for the short acknowledgement to finish so it is not recognised as the question.
        delay(200)
        var waited = 0
        while (graph.voiceOutput.speaking.value && waited < 3000) { delay(100); waited += 100 }
        withContext(Dispatchers.Main) {
            graph.voiceInput.start(graph.settings.voiceLanguage.value) { text ->
                graph.session.send(text, viaVoice = true)
            }
        }
        // Wait until the question is taken and the answer has been spoken.
        delay(500)
        while (scope.isActive) {
            val listening = graph.voiceInput.state.value.let { it is ListenState.Listening || it is ListenState.Partial }
            if (!listening && !graph.session.busy.value && !graph.voiceOutput.speaking.value) break
            delay(300)
        }
        delay(500)
    }

    private fun loadModel(): MicroWakeWordModel {
        val json = JSONObject(assets.open("wake/hey_jarvis.json").bufferedReader().use { it.readText() })
        val micro = json.getJSONObject("micro")
        val bytes = assets.open("wake/hey_jarvis.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { rewind() }
        return MicroWakeWordModel(
            id = "hey_jarvis",
            model = model,
            probabilityCutoff = micro.getDouble("probability_cutoff").toFloat(),
            slidingWindowSize = micro.getInt("sliding_window_size"),
        )
    }

    override fun onDestroy() {
        scope.cancel()
        _status.value = "Off"
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Hey Jarvis listening", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 2, Intent(this, WakeWordService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Listening for \"Hey Jarvis\"")
            .setContentText("On this phone only. Nothing is recorded or sent.")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build())
            .build()
    }

    companion object {
        private const val TAG = "WakeWordService"
        private const val CHANNEL = "wake"
        private const val NOTIFICATION_ID = 77
        private const val SAMPLE_RATE = 16000
        private const val ACTION_STOP = "com.jarvisx.app.STOP_WAKE"

        private val _status = MutableStateFlow("Off")
        val status: StateFlow<String> = _status.asStateFlow()

        /** Must be called while JARVIS X is on screen (Android rule for microphone services). */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, WakeWordService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
