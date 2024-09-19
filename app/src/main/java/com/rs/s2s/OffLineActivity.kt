package com.rs.s2s

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rs.deepfilter.DeepFilterUtil
import com.rs.deepfilter.DeepFilterUtil.runInference
import com.rs.s2s.ui.theme.S2SAndroidClientTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import ai.onnxruntime.OnnxTensor
import com.rs.deepfilter.DeepFilterUtil.createInputTensor
import com.rs.deepfilter.DeepFilterUtil.runInference2
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date


class OffLineActivity : ComponentActivity() {
    private val TAG: String = OffLineActivity::class.simpleName.toString()
    private val sampleRate = 48000 // 48kHz
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val frameSize = (sampleRate / 100) * 2 // 每10ms的帧大小（16bit）
//    private val frameSize = (sampleRate / 100)
    private var audioRecord: AudioRecord? = null
//    private var isRecording = mutableStateOf(false)

//    private var outputFile: File? = null




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            S2SAndroidClientTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) {
//                    content = { paddingValues ->
//                        // Scaffold 的主体内容
//                        OffLineScreen()
//                    }
//                }
                OffLineScreen()
            }
        }
        DeepFilterUtil.initialize(this)


    }

    override fun onDestroy() {
        DeepFilterUtil.release()
        super.onDestroy()
    }

    fun onnxTensorToFloatArray(onnxTensor: OnnxTensor): FloatArray {
        // 从 OnnxTensor 中获取 FloatBuffer
        val floatBuffer: FloatBuffer = onnxTensor.floatBuffer

        // 创建一个 FloatArray
        val floatArray = FloatArray(floatBuffer.remaining())

        // 将 FloatBuffer 中的数据读取到 FloatArray
        floatBuffer.get(floatArray)

        return floatArray
    }

    // 处理音频数据
    private fun processAudioData(data: ByteArray, states:OnnxTensor, atten_lim_db:OnnxTensor):  Map<String, Any> ?{
        // 将数据传递给音频算法进行处理
        Log.d(TAG, "processAudioData: ${data.size}")
        val byteBuffer = ByteBuffer.wrap(data)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN) // 根据设备选择字节序

        // 创建 FloatArray 来存储转换后的 float 数组
        val floatArray = FloatArray(data.size / 2)

        // 读取 ByteBuffer 中的浮点数
        for (i in floatArray.indices) {
            floatArray[i] = byteBuffer.short.toFloat()
        }


//        Log.d(TAG, "floatArray size: ${floatArray.size}")

//        val inputData1 = FloatArray(480) { 0.5f }  // 示例输入数据1
        val inputData1 = floatArray
        val inputShape1 = longArrayOf(480)  // 形状1

//        val states = FloatArray(45304) { 0f }  // 示例输入数据2
//        val statesShape = longArrayOf(45304)  // 形状2

//        val inputData3 = FloatArray(1) { 0f }  // 示例输入数据2
//        val inputShape3 = longArrayOf(1)  // 形状2

        // 输入映射
//        val inputDataMap = mapOf(
//            "input_frame" to (inputData1 to inputShape1),
//            "states" to (states to statesShape),
//            "atten_lim_db" to (inputData3 to inputShape3)
//        )
//
//        val result = runInference(inputDataMap)

        val inputDataMap = mapOf(
            "input_frame" to createInputTensor(inputData1,inputShape1),
            "states" to states,
            "atten_lim_db" to atten_lim_db
        )
        val result = runInference2(inputDataMap)

//        val new_states = result!!.getValue("new_states") as OnnxTensor
//
//        val new_states_array = onnxTensorToFloatArray(new_states)
//        return new_states_array
        return result
//        return new_states.getFloatBuffer().get(0) as FloatArray
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

    fun floatArrayToByteArray(floatArray: FloatArray): ByteArray {
        // 创建一个容量为 floatArray 大小的 4 倍的 ByteBuffer
        val byteBuffer = ByteBuffer.allocate(floatArray.size * 2)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)  // 确保字节顺序与系统一致，通常使用小端序

        // 将 floatArray 中的每个 float 转换为 4 个字节并放入 ByteBuffer
        for (float in floatArray) {
            byteBuffer.putShort(float.toInt().toShort())
        }

        return byteBuffer.array()  // 返回 ByteArray
    }

    fun onnxTensorToByteArray(tensor: OnnxTensor): ByteArray {
        // Ensure the tensor contains FloatBuffer data
        val floatBuffer = tensor.floatBuffer

        // Allocate a ByteBuffer of sufficient size to hold the Float data
        val byteBuffer = ByteBuffer.allocate(floatBuffer.capacity() * 2) // Float is 4 bytes
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)  // Set the byte order to little endian

        // Transfer FloatBuffer data to ByteBuffer
        while (floatBuffer.hasRemaining()) {
            byteBuffer.putShort(floatBuffer.get().toInt().toShort())
        }

        return byteBuffer.array()  // Convert ByteBuffer to ByteArray
    }

    // 启动录音
    @SuppressLint("MissingPermission")
    private suspend fun startRecording(job: Job,context: Context, isRecording: Boolean) {

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBufferSize
        )

        audioRecord?.startRecording()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())

        val resFile = File(context.filesDir, "resource_${timestamp}.wav")
        val outputFile = File(context.filesDir, "result_${timestamp}.wav")

        val originalOutputStream = FileOutputStream(resFile)
        val processedOutputStream = FileOutputStream(outputFile)

        Log.d(TAG, "outputFile: ${outputFile}")

        // 开始录音，并处理音频数据
//        isRecording.value = true
        val buffer = ByteArray(frameSize)
//        Log.d(TAG, "scope.launch")
        writeWavHeader(originalOutputStream, sampleRate, 1, 16)
        writeWavHeader(processedOutputStream, sampleRate, 1, 16)


        val init_states = FloatArray(45304) { 0f }  // 示例输入数据2
        val statesShape = longArrayOf(45304)  // 形状2
        var states = createInputTensor(init_states,statesShape)

        val inputData3 = FloatArray(1) { 0f }  // 示例输入数据2
        val inputShape3 = longArrayOf(1)  // 形状2
        val atten_lim_db = createInputTensor(inputData3,inputShape3)

        var f = 0
        while (job.isActive && isRecording) {
//            Log.d(TAG, "isRecording:$isRecording")
            val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (readBytes > 0) {
                // 处理音频数据，例如传递给音频算法
//                f += 1
//                Log.d(TAG,"enhanced_audio_frame num :${f}")
                originalOutputStream.write(buffer,0,readBytes)
//                val startTime = System.currentTimeMillis()

                var result = processAudioData(buffer, states, atten_lim_db)
//                val endTime1 = System.currentTimeMillis()

                val new_states = result!!.getValue("new_states") as OnnxTensor
//                states = onnxTensorToFloatArray(new_states)
                states = new_states
//                val endTime2 = System.currentTimeMillis()

                val enhanced_audio_frame = result!!.getValue("enhanced_audio_frame") as OnnxTensor
//                val endTime = System.currentTimeMillis()

                processedOutputStream.write(onnxTensorToByteArray(enhanced_audio_frame))
//                val inferenceTime = (endTime - startTime) / 1000.0 // 以秒为单位
//                Log.d(TAG, "total Time: $inferenceTime seconds")
//                val inferenceTime2 = (endTime1 - startTime) / 1000.0 // 以秒为单位
//                val inferenceTime3 = (endTime2 - startTime) / 1000.0 // 以秒为单位
//                Log.d(TAG, "processAudioData Time: $inferenceTime2 seconds")
//                Log.d(TAG, "onnxTensorToFloatArray Time: $inferenceTime3 seconds")


            }

        }

//        FileOutputStream(resFile).use { fos ->
//            // 写入 WAV 文件头（先占位，稍后更新文件头信息）
//            writeWavHeader(fos, sampleRate, 1, 16)
//            while (job.isActive && isRecording) {
//                Log.d(TAG, "isRecording:$isRecording")
//                val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
//                if (readBytes > 0) {
//                    // 处理音频数据，例如传递给音频算法
//                    states = processAudioData(buffer,states)
//                    fos.write(buffer.array())
//                }
//            }
//            // 录音停止后，更新 WAV 文件头部信息
//            finalizeWavFile(outputFile)
//        }

        originalOutputStream.close()
        processedOutputStream.close()

        finalizeWavFile(resFile)
        finalizeWavFile(outputFile)



//        scope.launch {
//            withContext(Dispatchers.IO) {
//                var states = FloatArray(45304) { 0f }  // 示例输入数据2
//                FileOutputStream(outputFile).use { fos ->
//                    // 写入 WAV 文件头（先占位，稍后更新文件头信息）
//                    writeWavHeader(fos, sampleRate, 1, 16)
//                    while (isRecording) {
//                        Log.d(TAG, "isRecording:$isRecording")
//                        val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
//                        if (readBytes > 0) {
//                            // 处理音频数据，例如传递给音频算法
//                            states = processAudioData(buffer,states)
//                        }
//                        // 模拟每10ms处理一次数据
////                    kotlinx.coroutines.delay(10)
//                    }
//                    // 录音停止后，更新 WAV 文件头部信息
//                    finalizeWavFile(outputFile)
//                }
//
//            }
//        }
    }

    // 停止录音
    private fun stopRecording() {
        Log.d(TAG,"stopRecording")
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    fun writeWavHeader(
        outputStream: FileOutputStream,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int
    ) {
        Log.d(TAG, "writeWavHeader")

        // 创建 WAV 文件头
        val header = ByteArray(44)
        val byteRate = sampleRate * channels * bitDepth / 8

        // Chunk ID "RIFF"
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()

        // Chunk Size（文件大小-8，先留空，最后填充）
        // header[4] 到 header[7] 留空，稍后补上

        // Format "WAVE"
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()

        // Subchunk1 ID "fmt "
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()

        // Subchunk1 Size (PCM = 16)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // Audio Format (PCM = 1)
        header[20] = 1
        header[21] = 0

        // Num Channels
        header[22] = channels.toByte()
        header[23] = 0

        // Sample Rate
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        // Byte Rate (SampleRate * NumChannels * BitsPerSample/8)
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        // Block Align (NumChannels * BitsPerSample/8)
        header[32] = ((channels * bitDepth) / 8).toByte()
        header[33] = 0

        // Bits Per Sample
        header[34] = bitDepth.toByte()
        header[35] = 0

        // Subchunk2 ID "data"
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()

        // Subchunk2 Size（音频数据的大小，先留空，最后补上）
        // header[40] 到 header[43] 留空，稍后补上

        // 将 header 写入文件
        outputStream.write(header, 0, 44)
    }

    fun finalizeWavFile(outputFile: File) {
        Log.d(TAG, "finalizeWavFile")
        val wavFile = RandomAccessFile(outputFile, "rw")
        val fileSize = wavFile.length()
        val dataSize = fileSize - 44

        wavFile.seek(4)
        wavFile.write(intToByteArray((fileSize - 8).toInt())) // Chunk Size

        wavFile.seek(40)
        wavFile.write(intToByteArray(dataSize.toInt())) // Data Size

        wavFile.close()
    }

    fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte()
        )
    }


    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun OffLineScreen(){
//        var isConnected by remember { mutableStateOf(true) }
//        var isThinking by remember { mutableStateOf(false) }
        val context = LocalContext.current
        var isRecording by remember { mutableStateOf(false) }
        var recordJob: Job? by remember { mutableStateOf(null) }
        val scope = rememberCoroutineScope()



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

        val buttonColor = when {
            isRecording -> listOf(
                Color(0xFFB71C1C), // 自定义深红色
                Color(0xFFFF5252)  // 自定义浅红色
            ) // 已连接且按
            else -> listOf(
                Color(0xFF2E7D32), // 自定义深绿色
                Color(0xFF66BB6A)  // 自定义浅绿色
            ) // 已连接且抬起状态的渐变色
        }

        val buttonText = when {
            isRecording -> "录音中"
            else -> "长按录音"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
//            RecordingButton(isConnected = isConnected,isThinking=isThinking, onButtonClick = {
//                // 按下按钮后的操作
//                startRecording(scope,context)
//            })
//            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Box(contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    Log.d(TAG, "onPress")
                                    checkAndRequestPermission(context, launcher) {
                                        isRecording = true
//                                    startRecording(scope,context,isRecording)
                                        recordJob = scope.launch(Dispatchers.IO) {
                                            startRecording(recordJob!!, context, isRecording)
                                        }
                                        Log.d(TAG, "isRecording:{$isRecording}")
//                                    recordJob?.onJoin
                                    }
                                    val released = tryAwaitRelease()
                                    // 如果用户抬起按钮，停止录音
                                    if (released) {
                                        Log.d(TAG, "onPress released")
                                        isRecording = false
                                        Log.d(TAG, "isRecording:$isRecording")
                                        stopRecording()
                                        recordJob?.cancelAndJoin()  // 停止录音
                                        recordJob = null
                                    }
                                    Log.d(TAG, "onPress complete")
                                },
//                            onLongPress = {
//                                Log.d(TAG,"onLongPress")
//                                checkAndRequestPermission(context, launcher) {
//                                    isRecording = true
//                                }
//                            },
                            )
                        }
                        .size(120.dp)
                        .padding(16.dp)
            ) {
                    Canvas(
                        modifier = Modifier
                            .size(90.dp)
                    ) {
                        drawCircle(
                            brush = Brush.linearGradient(buttonColor), // 使用渐变效果填充颜色
                            radius = size.minDimension / 2
                        )
                        // 边框效果，用于增强3D感
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.2f),
                            radius = size.minDimension / 2,
                            style = Stroke(width = 6f)
                        )
                    }

                    Text(
                        text = buttonText,
                        color = Color.White,
                        fontSize = 14.sp
                    )
            }
//            }
        }

    }

    @Preview(showBackground = true, showSystemUi = true)
    @Composable
    fun OffLineScreenPreview() {
        S2SAndroidClientTheme {
            OffLineScreen()
        }
    }
}

