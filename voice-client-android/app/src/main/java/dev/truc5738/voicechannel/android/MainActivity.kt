package dev.truc5738.voicechannel.android

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.media.*
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import android.content.Context
import android.content.pm.PackageManager

class MainActivity : Activity() {
    private var socket: Socket? = null
    private var running = false
    private var wantConnection = false
    private var connectionThread: Thread? = null
    private var sessionToken: String? = null
    private var statusView: TextView? = null
    private val sampleRate = 16000
    private val frameBytes = sampleRate / 50 * 2
    private val magic = 0x4D564331
    private val pongType = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val host = EditText(this).apply { hint = "Gateway host" }
        val port = EditText(this).apply { hint = "26467"; setText("26467") }
        val pair = EditText(this).apply { hint = "Pair code (6 digits)" }
        val status = TextView(this).apply { text = "Disconnected" }
        statusView = status
        val button = Button(this).apply { text = "Connect" }
        val stop = Button(this).apply { text = "Stop" }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32,32,32,32)
            addView(host); addView(port); addView(pair)
            addView(status); addView(button); addView(stop)
        }
        setContentView(layout)
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
        sessionToken = getPreferences(Context.MODE_PRIVATE).getString("session_token", null)

        button.setOnClickListener {
            if (wantConnection) return@setOnClickListener
            wantConnection = true
            status.text = "Connecting..."
            connectionThread = Thread {
                var firstConnection = true
                while (wantConnection) {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            runOnUiThread { statusView?.text = "Microphone permission required" }
                            wantConnection = false
                            break
                        }
                        val hostValue = host.text.toString().trim()
                        val portValue = port.text.toString().trim().toIntOrNull() ?: 26467
                        if (hostValue.isBlank()) {
                            runOnUiThread { statusView?.text = "Enter gateway host" }
                            wantConnection = false
                            break
                        }
                        val s = Socket(hostValue, portValue)
                        socket = s
                        running = true
                        val credential = sessionToken?.let { "SESSION:" + it } ?: "PAIR:" + pair.text.toString()
                        try {
                            runVoice(s, credential)
                        } catch (ex: Exception) {
                            if (sessionToken != null && ex.message == "SESSION_REJECTED") {
                                sessionToken = null
                                getPreferences(Context.MODE_PRIVATE).edit().remove("session_token").apply()
                                runOnUiThread { statusView?.text = "Session expired - enter pair code" }
                                wantConnection = false
                            } else {
                                throw ex
                            }
                        }
                        if (!wantConnection) break
                    } catch (ex: Exception) {
                        running = false
                        try { socket?.close() } catch (_: Exception) {}
                        socket = null
                        if (wantConnection) runOnUiThread { statusView?.text = "Disconnected - retrying" }
                    }
                    if (wantConnection) {
                        try { Thread.sleep(if (firstConnection) 3000L else 2000L) } catch (_: InterruptedException) { break }
                    }
                    firstConnection = false
                }
            }.also { it.isDaemon = true; it.start() }
        }
        stop.setOnClickListener {
            wantConnection = false
            running = false
            try { socket?.close() } catch (_: Exception) {}
            runOnUiThread { statusView?.text = "Disconnected" }
        }
    }

    private fun runVoice(s: Socket, token: String) {
        val input = DataInputStream(BufferedInputStream(s.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
        var id = UUID(0L, 0L)
        send(output, 1, id, 0, token.toByteArray())

        val ack = ByteArray(29)
        readFully(input, ack)
        val ab = ByteBuffer.wrap(ack).order(ByteOrder.BIG_ENDIAN)
        if (ab.int != magic || ab.get().toInt() != 1) {
            if (token.startsWith("SESSION:")) throw IOException("SESSION_REJECTED")
            throw IOException("Pairing rejected")
        }
        val assignedMsb = ab.long
        val assignedLsb = ab.long
        ab.int
        val ackLength = ab.int
        if (ackLength <= 0 || ackLength > 64) throw IOException("Invalid gateway response")
        val ackPayload = ByteArray(ackLength)
        readFully(input, ackPayload)
        val ackText = String(ackPayload, Charsets.UTF_8)
        if (!ackText.startsWith("OK")) throw IOException("Pairing rejected")
        ackText.lineSequence().firstOrNull { it.startsWith("SESSION:") }?.substringAfter("SESSION:")?.takeIf { it.isNotBlank() }?.let {
            sessionToken = it
            getPreferences(Context.MODE_PRIVATE).edit().putString("session_token", it).apply()
        }
        id = UUID(assignedMsb, assignedLsb)
        runOnUiThread { statusView?.text = "Connected" }

        val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frameBytes * 4))
        val trackMin = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(trackMin, frameBytes * 4)).build()

        Thread {
            try {
                track.play()
                while (running) {
                    val header = ByteArray(29)
                    readFully(input, header)
                    val hb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                    if (hb.int != magic) break
                    val type = hb.get()
                    val msb = hb.long
                    val lsb = hb.long
                    val seq = hb.int
                    val len = hb.int
                    if (len < 0 || len > 16384) break
                    val data = ByteArray(len)
                    readFully(input, data)
                    if (type.toInt() == 2 && UUID(msb, lsb) != id) track.write(data, 0, data.size)
                    if (type.toInt() == 4) {
                        send(output, pongType, id, seq, ByteArray(0))
                    }
                }
            } catch (_: Exception) {}
            running = false
            track.stop()
            track.release()
        }.start()

        recorder.startRecording()
        val frame = ByteArray(frameBytes)
        var seq = 0
        try {
            while (running) {
                var off=0
                while (off < frame.size && running) {
                    val n=recorder.read(frame,off,frame.size-off)
                    if(n<=0) break
                    off+=n
                }
                if(off==frame.size) send(output,2,id,seq++,frame)
            }
        } finally {
            recorder.stop()
            recorder.release()
            try { send(output,3,id,0,ByteArray(0)) } catch (_: Exception) {}
            try { s.close() } catch (_: Exception) {}
            running=false
        }
    }

    private fun send(out: DataOutputStream,type:Int,id:UUID,seq:Int,payload:ByteArray) {
        synchronized(out) {
            out.writeInt(magic); out.writeByte(type)
            out.writeLong(id.mostSignificantBits); out.writeLong(id.leastSignificantBits)
            out.writeInt(seq); out.writeInt(payload.size); out.write(payload); out.flush()
        }
    }

    private fun readFully(input:InputStream,data:ByteArray) {
        var off=0
        while(off<data.size) {
            val n=input.read(data,off,data.size-off)
            if(n<0) throw EOFException()
            off+=n
        }
    }
}
