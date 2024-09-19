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
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

val Context.dataStore by preferencesDataStore("settings")

class MainActivity : ComponentActivity() {

    private val TAG = MainActivity::class.java.simpleName

    private val SAMPLE_RATE = 16000 // 采样率
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
    private var serverIP: String=""
    private var chatMessages by mutableStateOf(listOf<ChatMessage>())

    val SERVER_ADDRESS_KEY = stringPreferencesKey("server_address")


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求麦克风权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
        }

//        DeepFilterUtil.test(this)
//        DeepFilterUtil.initialize(this)




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

    // 保存服务器地址
    suspend fun saveServerAddress(context: Context, serverAddress: String) {
        serverIP = serverAddress
        context.dataStore.edit { settings ->
            settings[SERVER_ADDRESS_KEY] = serverAddress
        }
    }

    // 读取服务器地址
    fun getServerAddress(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[SERVER_ADDRESS_KEY]
        }
    }

    suspend fun connectToServer(host: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {  // 切换到 IO 线程进行网络操作
            try {
                sendSocket = Socket(host, 12345)
                receiveSocket = Socket(host, 12346)
                Log.e(TAG,"Connection Success")
                sendSocket!!.isConnected && receiveSocket!!.isConnected
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e(TAG,"Connection Failed: ${e.message}")
                false
            }
        }
    }

    private fun addMessage(message: ChatMessage) {
        chatMessages = chatMessages + message
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Preview(showBackground = true, showSystemUi = true)
    @Composable
    fun VoiceSocketApp() {
        val coroutineScope = rememberCoroutineScope()
        var showSettingDialog by remember { mutableStateOf(false) }
        var isThinking by remember { mutableStateOf(false) }
        val context = LocalContext.current

        var isConnected by remember { mutableStateOf(false) }

        var serverAddress by remember { mutableStateOf("") }


        // 读取持久化的服务器地址
        LaunchedEffect(Unit) {
            getServerAddress(context).collect { address ->
                serverIP = address ?: "192.168.1.31"
                serverAddress = serverIP
                Log.d(TAG, "get serverIP:$serverIP")
                isConnected = connectToServer(serverIP,1234)

            }
        }


        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    // 权限已授予
                    isRecording = true
                    isPlaying = true
                    coroutineScope.launch(Dispatchers.IO) { startSending() }
                    coroutineScope.launch(Dispatchers.IO) { startReceiving() }

                    Log.d(TAG,"stopRecording:{$isRecording}")
                } else {
                    // 权限被拒绝
                    Toast.makeText(context, "需要麦克风权限才能使用录音", Toast.LENGTH_SHORT).show()
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            IconButton(
                onClick = {
                    showSettingDialog = true // 点击图标显示对话框
                },
                modifier = Modifier
                    .align(Alignment.End)
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_settings), // 使用设置图标
                    tint = Color.Unspecified,
                    contentDescription = "Settings",
                    modifier = Modifier.size(48.dp)
                )
            }

            if (showSettingDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showSettingDialog = false // 点击对话框外部或取消按钮时关闭对话框
                    },
                    title = {
                        Text(text = "设置服务器地址")
                    },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = serverAddress,
                                onValueChange = { serverAddress = it },
                                label = { Text("Enter Socket Server IP Address") },
//                                placeholder = { Text(serverIP) }, 限制为单行
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Uri) // 设置键盘类型为数字)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    Log.d(TAG, "save serverIP:${serverAddress}")
                                    saveServerAddress(context, serverAddress)
                                }
                                showSettingDialog = false // 点击确定后关闭对话框
                            }
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showSettingDialog = false // 点击取消后关闭对话框
                            }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }

            RecordingButton(isConnected = isConnected,isThinking=isThinking, onPress = {
                Log.d(TAG,"onPress")
                checkAndRequestPermission(context, launcher) {
                    isRecording = true
                    isPlaying = true
                    coroutineScope.launch(Dispatchers.IO) { startSending() }
                    coroutineScope.launch(Dispatchers.IO) { startReceiving() }
                }
            }, onReleased = {
                Log.d(TAG,"onPress released")
                coroutineScope.launch(Dispatchers.IO) { stopStreaming() }
                isRecording = false
                isPlaying = false
            }
            )


//            Button(onClick = {
//                isRecording = true
//                isPlaying = true
////                coroutineScope.launch(Dispatchers.IO) { startStreaming() }
//                coroutineScope.launch(Dispatchers.IO) { startSending() }
//                coroutineScope.launch(Dispatchers.IO) { startReceiving() }
//            }) {
//                Text(text = "Start Streaming")
//            }

//            Spacer(modifier = Modifier.height(16.dp))
//
//            Button(onClick = {
//                stopStreaming()
//            }) {
//                Text(text = "Stop Streaming")
//            }

//            Spacer(modifier = Modifier.height(16.dp))
//
//            OutlinedTextField(
//                value = serverIP,
//                onValueChange = { serverIP = it },
//                label = { Text("Enter Socket Server IP Address") },
//                placeholder = { Text("Enter Socket Server IP Address") },
//
//            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startSending() {
        try {
            // 连接到负责发送数据的Socket
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
//            receiveSocket = Socket(serverIP, 12346)
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
    }

    override fun onDestroy() {
        stopStreaming()
        try {
            sendSocket?.close()
            receiveSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    // 检查并请求麦克风权限
    fun checkAndRequestPermission(context: Context, launcher: androidx.activity.result.ActivityResultLauncher<String>, onGranted: () -> Unit) {
        val permission = Manifest.permission.RECORD_AUDIO
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            launcher.launch(permission)
        } else {
            onGranted()
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