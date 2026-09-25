package dev.truc5738.voicechannel.android

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.widget.*
import java.io.*
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity() {
    companion object {
        private const val MAGIC = 0x4D564331
        private const val HELLO: Byte = 1
        private const val AUDIO: Byte = 2
        private const val GOODBYE: Byte = 3
        private const val PING: Byte = 4
        private const val PONG: Byte = 5
        private const val SAMPLE_RATE = 16000
        private const val FRAME_BYTES = 640
        private const val REQUEST_AUDIO = 42
    }
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var uuid: EditText
    private lateinit var pair: EditText
    private lateinit var status: TextView
    private var socket: Socket? = null
    private var out: DataOutputStream? = null
    @Volatile private var running = false
    private var assignedUuid = UUID(0L, 0L)
    private var sequence = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32) }
        fun field(h: String) = EditText(this).apply { hint=h; singleLine=true }
        host=field("Gateway host")
        port=field("Gateway port (26467)"); port.setText("26467")
        uuid=field("Minecraft UUID (normal mode)")
        pair=field("6-digit pairing code")
        status=TextView(this).apply { text="Disconnected" }
        root.addView(host); root.addView(port); root.addView(uuid); root.addView(pair)
        root.addView(Button(this).apply { text="Connect"; setOnClickListener { connect() } })
        root.addView(Button(this).apply { text="Disconnect"; setOnClickListener { disconnect() } })
        root.addView(status)
        setContentView(root)
    }

    private fun connect() {
        if (running) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            status.text="Microphone permission required"; return
        }
        val h=host.text.toString().trim()
        val p=port.text.toString().trim().toIntOrNull() ?: 26467
        val code=pair.text.toString().trim()
        val u=uuid.text.toString().trim()
        val pairing=code.matches(Regex("\\d{6}"))
        if (h.isEmpty() || (!pairing && runCatching { UUID.fromString(u) }.isFailure)) {
            status.text="Enter gateway host and either a UUID or 6-digit pairing code"; return
        }
        thread(name="VoiceGateway") {
            try {
                val self=if(pairing) UUID(0L,0L) else UUID.fromString(u)
                val token=if(pairing) "PAIR:"+code else "SESSION:"+u
                val s=Socket(h,p); s.tcpNoDelay=true; socket=s
                val input=DataInputStream(BufferedInputStream(s.getInputStream()))
                val output=DataOutputStream(BufferedOutputStream(s.getOutputStream())); out=output
                send(output,HELLO,self,0,token.toByteArray(StandardCharsets.UTF_8))
                assignedUuid=readHelloAck(input,self) ?: throw IOException("Gateway authentication rejected")
                running=true
                runOnUiThread { status.text="Connected as "+assignedUuid }
                val receiver=thread(name="VoiceReceiver") { receive(input,output) }
                captureMicrophone(output)
                running=false; receiver.join(500)
            } catch(e:Exception) {
                runOnUiThread { status.text="Disconnected: "+(e.message ?: "connection error") }
            } finally { disconnect() }
        }
    }

    private fun captureMicrophone(output: DataOutputStream) {
        val min=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        if(min<=0) throw IOException("Microphone is unavailable")
        val recorder=AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min,FRAME_BYTES*4))
        recorder.startRecording()
        val frame=ByteArray(FRAME_BYTES)
        try {
            while(running) {
                var offset=0
                while(offset<frame.size && running) {
                    val n=recorder.read(frame,offset,frame.size-offset)
                    if(n<=0) throw IOException("Microphone read failed")
                    offset+=n
                }
                if(running) send(output,AUDIO,assignedUuid,sequence++,frame)
            }
        } finally { recorder.stop(); recorder.release() }
    }

    private fun receive(input:DataInputStream,output:DataOutputStream) {
        val track=AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(FRAME_BYTES*8).setTransferMode(AudioTrack.MODE_STREAM).build()
        track.play()
        try {
            while(running) {
                if(input.readInt()!=MAGIC) throw IOException("Invalid gateway packet")
                val type=input.readByte(); val sender=UUID(input.readLong(),input.readLong())
                val seq=input.readInt(); val length=input.readInt()
                if(length<0 || length>16384) throw IOException("Invalid audio frame")
                val payload=ByteArray(length); input.readFully(payload)
                when {
                    type==AUDIO && sender!=assignedUuid -> track.write(payload,0,payload.size)
                    type==PING -> send(output,PONG,assignedUuid,seq,ByteArray(0))
                }
            }
        } catch(_:IOException) {} finally { track.stop(); track.release() }
    }

    private fun readHelloAck(input:DataInputStream,self:UUID):UUID? {
        if(input.readInt()!=MAGIC || input.readByte()!=HELLO) return null
        val assigned=UUID(input.readLong(),input.readLong()); input.readInt()
        val length=input.readInt()
        if(length<=0 || length>1024) return null
        val payload=ByteArray(length); input.readFully(payload)
        if(!String(payload,StandardCharsets.UTF_8).startsWith("OK")) return null
        if(self.mostSignificantBits!=0L && assigned!=self) return null
        return assigned
    }

    @Synchronized private fun send(o:DataOutputStream,type:Byte,id:UUID,seq:Int,payload:ByteArray) {
        o.writeInt(MAGIC); o.writeByte(type.toInt()); o.writeLong(id.mostSignificantBits); o.writeLong(id.leastSignificantBits)
        o.writeInt(seq); o.writeInt(payload.size); o.write(payload); o.flush()
    }

    private fun disconnect() {
        running=false
        try { out?.let { send(it,GOODBYE,assignedUuid,0,ByteArray(0)) } } catch(_:Exception) {}
        try { socket?.close() } catch(_:Exception) {}
        socket=null; out=null
        runOnUiThread { status.text="Disconnected" }
    }

    override fun onDestroy() { disconnect(); super.onDestroy() }
}
