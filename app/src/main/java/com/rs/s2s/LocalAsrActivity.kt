package com.rs.s2s

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.test.core.app.ApplicationProvider
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.getEndpointConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getModelConfig
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class LocalAsrActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private val TAG: String = LocalAsrActivity::class.simpleName.toString()

    private val permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    private lateinit var onlineRecognizer: OnlineRecognizer
    private lateinit var offlineRecognizer: OfflineRecognizer
    private var audioRecord: AudioRecord? = null

    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO

    private var samplesBuffer = arrayListOf<FloatArray>()
    private val audioSource = MediaRecorder.AudioSource.MIC


    // Note: We don't use AudioFormat.ENCODING_PCM_FLOAT
    // since the AudioRecord.read(float[]) needs API level >= 23
    // but we are targeting API level >= 21
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
//    private var idx: Int = 0
    private var lastText: String = ""
    private val viewModel by viewModels<MainViewModel>()

    private lateinit var tts: TextToSpeech

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

        Log.i(TAG, "Start to initialize first-pass recognizer")
        initOnlineRecognizer()
        Log.i(TAG, "Finished initializing first-pass recognizer")

        Log.i(TAG, "Start to initialize second-pass recognizer")
        initOfflineRecognizer()
        Log.i(TAG, "Finished initializing second-pass recognizer")

        // 初始化 TTS
        tts = TextToSpeech(this, this)

        setContent {
//            OffLineScreen()
            ChatScreen()
        }
    }

    @SuppressLint("CoroutineCreationDuringComposition")
    @Composable
    fun ChatScreen(){
        var chatMessages by remember {mutableStateOf(arrayListOf<ChatMessage>())}
        var isRecording by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        var context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    // 权限已授予
//                    recordJob = scope.launch {
//                        startRecording(scope,context,isRecording)
//                    }
//                    isRecording = true
//                    Log.d(TAG, "stopRecording:{$isRecording}")
                } else {
                    // 权限被拒绝
                    Toast.makeText(context, "需要麦克风权限才能使用录音", Toast.LENGTH_SHORT).show()
                }
            }
        )
        var recordJob: Job? by remember { mutableStateOf(null) }
//        chatMessages.add(ChatMessage("测试测试测试车的精神",false))
//        val generationConfig = generationConfig {
//            context = ApplicationProvider.getApplicationContext() // required
//            temperature = 0.2f
//            topK = 16
//            maxOutputTokens = 256
//        }
//        val generativeModel = GenerativeModel(
//            generationConfig = generationConfig,
//        )
//
//        scope.launch {
//            // Single string input prompt
//            val input = "你好"
//            val response = generativeModel.generateContent(input)
//            print(response.text)
//        }

        ChatScreen(
            isRecording = isRecording,
            messages = chatMessages,
            serverAddress = viewModel.getServerUrl(),
            apiKey = viewModel.getApiKey(),
            modelName = viewModel.getModelName(),
            onStartRecording = {
                // 开始录音逻辑
                checkAndRequestPermission(context, launcher) {
                    isRecording = true
                    recordJob = scope.launch(Dispatchers.IO) {
                        startRecording(recordJob!!, context, isRecording, true,
                            chatMessages
                        )
                    }
                    Log.d(TAG, "isRecording:{$isRecording}")
//                                    recordJob?.onJoin
                }
            },
            onStopRecording = {
                // 停止录音逻辑
                Log.d(TAG, "onPress released")
                if (viewModel.getApiKey().isEmpty() || viewModel.getServerUrl().isEmpty()){
                    Toast.makeText(context, "请在设置中输入服务地址或API_KEY", Toast.LENGTH_LONG).show()
                }else{
                    isRecording = false
                    Log.d(TAG, "isRecording:$isRecording")
                    stopRecording()
                    scope.launch(Dispatchers.IO){
                        recordJob?.cancelAndJoin()  // 停止录音
                        recordJob = null
                    }

                    println(chatMessages)
                    if (chatMessages.isNotEmpty()){
                        val msg = chatMessages.last().text
                        println(msg)
                        if (msg.isNotEmpty()){
                            scope.launch(Dispatchers.IO) {
                                viewModel.sendDeepSeekRequest(msg,object : Callback{
                                    override fun onFailure(call: Call, e: IOException) {
                                        println(e.message)
                                        chatMessages = ArrayList(chatMessages).apply { add(ChatMessage("请求失败",false)) }
                                    }

                                    override fun onResponse(call: Call, response: Response) {
                                        println(response)
                                        response.use {
                                            if (!response.isSuccessful) {
                                                println("Unexpected code $response")
                                                chatMessages = ArrayList(chatMessages).apply { add(ChatMessage("Unexpected code $response",false)) }
                                            } else {
                                                val responseBody = response.body?.string()
                                                println("Response: $responseBody")
                                                val json = JSONObject(responseBody)
                                                val content = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                                                chatMessages = ArrayList(chatMessages).apply { add(ChatMessage(content,false)) }
                                                speakOut(content)
                                            }
                                        }
                                    }

                                })
                            }

                        }
                    }
                }

            },
            onSave = { url, apikey,modelName ->
                Log.i(TAG, "onSave url: $url apikey: $apikey modelName:$modelName")
                scope.launch {
                    viewModel.setSavedConfig(url,apikey,modelName)
                }
            }
        )
    }



    @Preview(showBackground = true, showSystemUi = true)
    @Composable
    fun OffLineScreenPreview() {
        ChatScreen()
    }


    // 启动录音
    @SuppressLint("MissingPermission")
    private suspend fun startRecording(job: Job,context: Context, isRecording: Boolean, isAEC: Boolean, message: MutableList<ChatMessage>) {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        println(minBufferSize)
        val audioFormat = AudioFormat.Builder()
            .setEncoding(audioFormat)
            .setSampleRate(sampleRateInHz)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        audioRecord = AudioRecord.Builder()
            .setAudioSource(audioSource)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferSize)
            .build().apply {
                AcousticEchoCanceler.create(this.audioSessionId)?.apply {
                    enabled = isAEC
                }
            }




        audioRecord?.startRecording()
        samplesBuffer.clear()
        lastText = ""
        processSamples(job, isRecording, message)
    }



    private fun processSamples(job: Job, isRecording:Boolean, chatMessage: MutableList<ChatMessage>) {
        Log.i(TAG, "processing samples")
        val stream = onlineRecognizer.createStream()

        val interval = 0.1 // i.e., 100 ms
        val bufferSize = (interval * sampleRateInHz).toInt() // in samples
        val buffer = ShortArray(bufferSize)
        println(buffer.size) //160
        runOnUiThread {
            chatMessage.add(ChatMessage("",true))
        }
        while (job.isActive && isRecording) {
            val ret = audioRecord?.read(buffer, 0, buffer.size)
            if (ret != null && ret > 0) {
                val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                samplesBuffer.add(samples)

                stream.acceptWaveform(samples, sampleRate = sampleRateInHz)
                while (onlineRecognizer.isReady(stream)) {
                    onlineRecognizer.decode(stream)
                }
                val isEndpoint = onlineRecognizer.isEndpoint(stream)
                var textToDisplay = lastText

                var text = onlineRecognizer.getResult(stream).text
                if (text.isNotBlank()) {
//                    textToDisplay = "$text"
                    textToDisplay = if (lastText.isBlank()) {
                        "$text"
                    } else {
                        "${lastText}$text"
                    }
                }

                if (isEndpoint) {
                    onlineRecognizer.reset(stream)
                    if (text.isNotBlank()) {
                        text = runSecondPass()
                        lastText = "${lastText}$text"
                    } else {
                        samplesBuffer.clear()
                    }
                    Log.d(TAG, "asr isEndpoint text:$textToDisplay")
                }
                Log.d(TAG, "asr text:$textToDisplay")

                runOnUiThread {
                    chatMessage.last().text = textToDisplay.lowercase()
                }
            }
        }
        stream.release()
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



    // 停止录音
    private fun stopRecording() {
        Log.d(TAG,"stopRecording")
        audioRecord!!.stop()
        audioRecord!!.release()
        audioRecord = null
    }



    private fun initOnlineRecognizer() {
        // Please change getModelConfig() to add new models
        // See https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html
        // for a list of available models
        val firstType = 9
        val firstRuleFsts: String?
        firstRuleFsts = null
        Log.i(TAG, "Select model type $firstType for the first pass")
        val config = OnlineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
            modelConfig = getModelConfig(type = firstType)!!,
            endpointConfig = getEndpointConfig(),
            enableEndpoint = true,
        )
        if (firstRuleFsts != null) {
            config.ruleFsts = firstRuleFsts;
        }

        onlineRecognizer = OnlineRecognizer(
            assetManager = assets,
            config = config,
        )
    }

    private fun initOfflineRecognizer() {
        // Please change getOfflineModelConfig() to add new models
        // See https://k2-fsa.github.io/sherpa/onnx/pretrained_models/index.html
        // for a list of available models
        val secondType = 0
        var secondRuleFsts: String?
        secondRuleFsts = null
        Log.i(TAG, "Select model type $secondType for the second pass")

        val config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = sampleRateInHz, featureDim = 80),
            modelConfig = getOfflineModelConfig(type = secondType)!!,
        )

        if (secondRuleFsts != null) {
            config.ruleFsts = secondRuleFsts
        }

        offlineRecognizer = OfflineRecognizer(
            assetManager = assets,
            config = config,
        )
    }

    private fun runSecondPass(): String {
        var totalSamples = 0
        for (a in samplesBuffer) {
            totalSamples += a.size
        }
        var i = 0

        val samples = FloatArray(totalSamples)

        // todo(fangjun): Make it more efficient
        for (a in samplesBuffer) {
            for (s in a) {
                samples[i] = s
                i += 1
            }
        }


        val n = maxOf(0, samples.size - 8000)

        samplesBuffer.clear()
        samplesBuffer.add(samples.sliceArray(n until samples.size))

        val stream = offlineRecognizer.createStream()
        stream.acceptWaveform(samples.sliceArray(0..n), sampleRateInHz)
        offlineRecognizer.decode(stream)
        val result = offlineRecognizer.getResult(stream)

        stream.release()

        return result.text
    }

    private fun speakOut(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 设置语言
            val result = tts.setLanguage(Locale.CHINA)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS 语言不支持")
            } else {
                // 可以开始朗读
//                speakOut("Hello, welcome to the app!")
                Log.i(TAG, "TTS init success")
            }
        } else {
            Log.e(TAG, "TTS 初始化失败 status:$status")
        }
    }


    override fun onDestroy() {
        // 释放资源
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}