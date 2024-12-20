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
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
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
import androidx.compose.ui.draw.alpha
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
//import com.webrtc.audioprocessing.WebrtcAPMActivity
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

//    private lateinit var onlineRecognizer: OnlineRecognizer
//    private lateinit var offlineRecognizer: OfflineRecognizer
    private var samplesBuffer = arrayListOf<FloatArray>()



    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求麦克风权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
        }
//        Log.i(TAG, "Start to initialize first-pass recognizer")
//        initOnlineRecognizer()
//        Log.i(TAG, "Finished initializing first-pass recognizer")
//
//        Log.i(TAG, "Start to initialize second-pass recognizer")
//        initOfflineRecognizer()
//        Log.i(TAG, "Finished initializing second-pass recognizer")

        setContent {
            VoiceSocketApp()
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


        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    // 权限已授予
//                    isRecording = true
//                    isPlaying = true
//                    coroutineScope.launch(Dispatchers.IO) { startSending() }
//                    coroutineScope.launch(Dispatchers.IO) { startReceiving() }
//
//                    Log.d(TAG,"stopRecording:{$isRecording}")
                } else {
                    // 权限被拒绝
                    Toast.makeText(context, "需要麦克风权限才能使用录音", Toast.LENGTH_SHORT).show()
                }
            }
        )

        // 读取持久化的服务器地址
        LaunchedEffect(Unit) {
            getServerAddress(context).collect { address ->
                serverIP = address ?: "192.168.1.31"
                serverAddress = serverIP
                Log.d(TAG, "get serverIP:$serverIP")
                isConnected = connectToServer(serverIP,1234)
                if (isConnected){
                    checkAndRequestPermission(context, launcher) {
                        isRecording = true
                        isPlaying = true
                        coroutineScope.launch(Dispatchers.IO) { startSending() }
                        coroutineScope.launch(Dispatchers.IO) { startReceiving() }
                    }
                }else {
                    coroutineScope.launch(Dispatchers.IO) { stopStreaming() }
                    isRecording = false
                    isPlaying = false
                }

            }
        }




        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


            Column(Modifier.align(Alignment.End)) {
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

//                IconButton(
//                    onClick = {
//                        // 跳转到目标Activity
//                        val intent = Intent(context, OffLineActivity::class.java)
////                        val intent = Intent(context, WebrtcAPMActivity::class.java)
//                        startActivity(intent)
//                    },
//                    modifier = Modifier
//                        .align(Alignment.End)
//                ) {
//                    Icon(
//                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_offline_bolt), // 使用设置图标
//                        tint = Color.Unspecified,
//                        contentDescription = "offline",
//                        modifier = Modifier.size(48.dp)
//                    )
//                }

//                IconButton(
//                    onClick = {
//                        // 跳转到目标Activity
//                        val intent = Intent(context, WebrtcAPMActivity::class.java)
//                        startActivity(intent)
//                    },
//                    modifier = Modifier
//                        .align(Alignment.End)
//                ) {
//                    Icon(
//                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_offline_bolt), // 使用设置图标
//                        tint = Color.Unspecified,
//                        contentDescription = "offline",
//                        modifier = Modifier.size(48.dp)
//                    )
//                }
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
//                checkAndRequestPermission(context, launcher) {
//                    isRecording = true
//                    isPlaying = true
//                    coroutineScope.launch(Dispatchers.IO) { startSending() }
//                    coroutineScope.launch(Dispatchers.IO) { startReceiving() }
//                }
            }, onReleased = {
                Log.d(TAG,"onPress released")
//                coroutineScope.launch(Dispatchers.IO) { stopStreaming() }
//                isRecording = false
//                isPlaying = false
            }
            )


        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startSending() {
        try {
            // 连接到负责发送数据的Socket
            outputStream = sendSocket?.getOutputStream()
//            val stream = onlineRecognizer.createStream()


            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)


            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()

            recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize)
                .build().apply {
                    AcousticEchoCanceler.create(this.audioSessionId)?.apply {
                        enabled = true
                    }
                }

            recorder?.startRecording()

            val buffer = ByteArray(BUFFER_SIZE)

            while (isRecording) {
                val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    if (isRecordMute){ //tts 播报中
                        val silenceBuffer = ByteArray(BUFFER_SIZE)
                        outputStream?.write(silenceBuffer)
                    }else{
                        outputStream?.write(buffer, 0, read)
//                        val samples = FloatArray(read) { buffer[it] / 32768.0f }
//                        samplesBuffer.add(samples)
//                        stream.acceptWaveform(samples, sampleRate = SAMPLE_RATE)
//                        while (onlineRecognizer.isReady(stream)) {
//                            onlineRecognizer.decode(stream)
//                        }
//                        val isEndpoint = onlineRecognizer.isEndpoint(stream)
//                        var textToDisplay = lastText
//
//                        var text = onlineRecognizer.getResult(stream).text
//                        if (text.isNotBlank()) {
//                            textToDisplay = if (lastText.isBlank()) {
//                                // textView.text = "${idx}: ${text}"
//                                "${idx}: $text"
//                            } else {
//                                "${lastText}\n${idx}: $text"
//                            }
//                        }
//
//                        if (isEndpoint) {
//                            onlineRecognizer.reset(stream)
//
//                            if (text.isNotBlank()) {
//                                text = runSecondPass()
//                                lastText = "${lastText}\n${idx}: $text"
//                                idx += 1
//                            } else {
//                                samplesBuffer.clear()
//                            }
//                        }
//
//                        runOnUiThread {
//                            textView.text = textToDisplay.lowercase()
//                        }
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

//    private fun initOnlineRecognizer() {
//        // Please change getModelConfig() to add new models
//        // See https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html
//        // for a list of available models
//        val firstType = 9
//        val firstRuleFsts: String?
//        firstRuleFsts = null
//        Log.i(TAG, "Select model type $firstType for the first pass")
//        val config = OnlineRecognizerConfig(
//            featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
//            modelConfig = getModelConfig(type = firstType)!!,
//            endpointConfig = getEndpointConfig(),
//            enableEndpoint = true,
//        )
//        if (firstRuleFsts != null) {
//            config.ruleFsts = firstRuleFsts;
//        }
//
//        onlineRecognizer = OnlineRecognizer(
//            assetManager = application.assets,
//            config = config,
//        )
//    }
//
//    private fun initOfflineRecognizer() {
//        // Please change getOfflineModelConfig() to add new models
//        // See https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html
//        // for a list of available models
//        val secondType = 0
//        var secondRuleFsts: String?
//        secondRuleFsts = null
//        Log.i(TAG, "Select model type $secondType for the second pass")
//
//        val config = OfflineRecognizerConfig(
//            featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
//            modelConfig = getOfflineModelConfig(type = secondType)!!,
//        )
//
//        if (secondRuleFsts != null) {
//            config.ruleFsts = secondRuleFsts
//        }
//
//        offlineRecognizer = OfflineRecognizer(
//            assetManager = application.assets,
//            config = config,
//        )
//    }
//
//    private fun runSecondPass(): String {
//        var totalSamples = 0
//        for (a in samplesBuffer) {
//            totalSamples += a.size
//        }
//        var i = 0
//
//        val samples = FloatArray(totalSamples)
//
//        // todo(fangjun): Make it more efficient
//        for (a in samplesBuffer) {
//            for (s in a) {
//                samples[i] = s
//                i += 1
//            }
//        }
//
//
//        val n = maxOf(0, samples.size - 8000)
//
//        samplesBuffer.clear()
//        samplesBuffer.add(samples.sliceArray(n until samples.size))
//
//        val stream = offlineRecognizer.createStream()
//        stream.acceptWaveform(samples.sliceArray(0..n), SAMPLE_RATE)
//        offlineRecognizer.decode(stream)
//        val result = offlineRecognizer.getResult(stream)
//
//        stream.release()
//
//        return result.text
//    }

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

}