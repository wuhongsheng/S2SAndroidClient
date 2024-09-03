package com.rs.s2s

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class MainActivity : ComponentActivity() {

    private val SAMPLE_RATE = 16000 // 采样率
//    private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
//        SAMPLE_RATE,
//        AudioFormat.CHANNEL_IN_MONO,
//        AudioFormat.ENCODING_PCM_16BIT
//    )

    private val BUFFER_SIZE = 1024

    private var isRecording by mutableStateOf(false)
    private var isPlaying by mutableStateOf(false)
    private var isRecordMute by mutableStateOf(false)


    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null

    private var sendSocket: Socket? = null
    private var receiveSocket: Socket? = null

    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val serverIP: String = "192.168.23.177"
    private var chatMessages by mutableStateOf(listOf<ChatMessage>())

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求麦克风权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
        }

        setContent {
            VoiceSocketApp()
//            ChatScreen(
//                messages = chatMessages,
//                onSendMessage = { message, isVoice ->
//                    addMessage(ChatMessage(text = message, isUser = true, isVoice = isVoice))
//                    // 模拟接收响应或使用实际的语音合成/接收逻辑
//                    if (isVoice) {
//                        addMessage(ChatMessage(text = "收到语音消息", isUser = false, isVoice = true))
//                    } else {
//                        addMessage(ChatMessage(text = "服务器响应: $message", isUser = false))
//                    }
//                },
//                onStartRecording = {
//                    // 开始录音逻辑
//                    // 你可以在这里调用 MediaRecorder 或其他录音工具
//                },
//                onStopRecording = {
//                    // 停止录音逻辑
//                    // 停止录音后可以获取音频文件或数据
//                }
//            )
        }
    }

    private fun addMessage(message: ChatMessage) {
        chatMessages = chatMessages + message
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Composable
    fun VoiceSocketApp() {
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                isRecording = true
                isPlaying = true
//                coroutineScope.launch(Dispatchers.IO) { startStreaming() }
                coroutineScope.launch(Dispatchers.IO) { startSending() }
                coroutineScope.launch(Dispatchers.IO) { startReceiving() }
            }) {
                Text(text = "Start Streaming")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                stopStreaming()
            }) {
                Text(text = "Stop Streaming")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startSending() {
        try {
            // 连接到负责发送数据的Socket
            sendSocket = Socket(serverIP, 12345)
            outputStream = sendSocket?.getOutputStream()

            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE
            )

            recorder?.startRecording()

            val buffer = ByteArray(BUFFER_SIZE)

            while (isRecording) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    if (isRecordMute){
                        val silenceBuffer = ByteArray(BUFFER_SIZE)
                        outputStream?.write(silenceBuffer)
                    }else{
                        outputStream?.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    val handler = Handler(Looper.getMainLooper())


    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun startReceiving() {
        try {
            // 连接到负责接收数据的Socket
            receiveSocket = Socket(serverIP, 12346)
            inputStream = receiveSocket?.getInputStream()

            player = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(BUFFER_SIZE)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            player?.play()

            val receiveBuffer = ByteArray(BUFFER_SIZE)
            val task = Runnable {
                println("延时后: ${System.currentTimeMillis()}")
                isRecordMute = false
            }

            while (isPlaying) {
                val read = inputStream?.read(receiveBuffer, 0, receiveBuffer.size) ?: 0
                if (read > 0) {
                    println("read > 0")
                    isRecordMute = true
                    player?.write(receiveBuffer, 0, read)
                    if (handler.hasCallbacks(task)){
                        handler.removeCallbacks(task)
                    }
                    handler.postDelayed(task, 150)
                }

            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

//    private fun startStreaming() {
//        try {
//            // 连接服务器
//            socket = Socket("192.168.31.210", 5000)
//            outputStream = socket?.getOutputStream()
//
//
//            inputStream = socket?.getInputStream()
//
//            if (ActivityCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.RECORD_AUDIO
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                // TODO: Consider calling
//                //    ActivityCompat#requestPermissions
//                // here to request the missing permissions, and then overriding
//                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                //                                          int[] grantResults)
//                // to handle the case where the user grants the permission. See the documentation
//                // for ActivityCompat#requestPermissions for more details.
//                return
//            }
//            recorder = AudioRecord(
//                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
//                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE
//            )
//
////            player = AudioTrack(
////                AudioFormat.STREAM_MUSIC, SAMPLE_RATE,
////                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE,
////                AudioTrack.MODE_STREAM
////            )
//
//            player = AudioTrack.Builder()
//                .setAudioAttributes(
//                    android.media.AudioAttributes.Builder()
//                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
//                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
//                        .build()
//                )
//                .setAudioFormat(
//                    AudioFormat.Builder()
//                        .setSampleRate(SAMPLE_RATE)
//                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
//                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
//                        .build()
//                )
//                .setBufferSizeInBytes(BUFFER_SIZE)
//                .setTransferMode(AudioTrack.MODE_STREAM)
//                .build()
//
//            recorder?.startRecording()
//            player?.play()
//
//            val buffer = ByteArray(BUFFER_SIZE)
//
//            while (isRecording) {
//                // 从麦克风读取音频数据
//                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
//                if (read > 0) {
//                    // 发送音频数据到服务器
//                    outputStream?.write(buffer, 0, read)
//                }
//            }
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//
//        // 接收数据并播放
//        val receiveBuffer = ByteArray(BUFFER_SIZE)
//
//        while (isPlaying) {
//            try {
//                // 从服务器接收音频数据
//                val read = inputStream?.read(receiveBuffer, 0, receiveBuffer.size) ?: 0
//                if (read > 0) {
//                    // 播放接收到的音频数据
//                    player?.write(receiveBuffer, 0, read)
//                }
//            } catch (e: IOException) {
//                e.printStackTrace()
//            }
//        }
//    }

    private fun stopStreaming() {
        isRecording = false
        isPlaying = false

        recorder?.stop()
        recorder?.release()
        recorder = null

        player?.stop()
        player?.release()
        player = null

        try {
            sendSocket?.close()
            receiveSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

//    override fun onRequestPermissionsResult(
//        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//
//        if (requestCode == 200) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                // Permission granted
//            } else {
//                // Permission denied
//            }
//        }
//    }
}