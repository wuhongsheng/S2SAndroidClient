package com.rs.deepfilter

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.extensions.OrtxPackage
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.FloatBuffer


/**
 * description
 * @author whs
 * @date 2024/9/13
 */
object DeepFilterUtil {
    private val TAG: String=DeepFilterUtil.javaClass.simpleName
    private lateinit var env: OrtEnvironment
    private lateinit var session: OrtSession

    // 初始化 ONNX Runtime 环境并加载模型
    fun initialize(context: Context) {
        try {
            val model_path = copyAssetToStorage(context,"deepfilter.onnx",context.filesDir.absolutePath)
            env = OrtEnvironment.getEnvironment()
//            val modelFile = File(context.filesDir, modelFileName)
            // 创建 NNAPI 推理选项
            val sessionOptions = OrtSession.SessionOptions()
//            sessionOptions.addNnapi()
            sessionOptions.registerCustomOpLibrary(OrtxPackage.getLibraryPath())
            session = env.createSession(model_path, sessionOptions)
            Log.d(TAG, "Model loaded successfully.")
        } catch (e: OrtException) {
            Log.e(TAG, "Error initializing ONNX model: ${e.message}")
        }
    }

    fun copyAssetToStorage(context: Context, fileName: String?, destinationPath: String?): String? {
        val assetManager = context.assets
        val outFile = File(destinationPath, fileName)

        try {
            assetManager.open(fileName!!).use { inputStream ->
                FileOutputStream(outFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while ((inputStream.read(buffer).also { length = it }) > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

        return outFile.absolutePath
    }

    // 创建输入张量
    fun createInputTensor(inputData: Any, inputShape: LongArray): OnnxTensor {
       if (inputData is ByteArray){
           println("createInputTensor ByteArray")
           val inputTensor = OnnxTensor.createTensor(
            env,
            ByteBuffer.wrap(inputData),
                inputShape,
            OnnxJavaType.INT16)
            return inputTensor
        }else{
           println("createInputTensor FloatArray")
           val floatBuffer = FloatBuffer.wrap(inputData as FloatArray?)
           return OnnxTensor.createTensor(env, floatBuffer, inputShape)
        }
    }

    fun test(context: Context){
        initialize(context)
        println("initialize success")
        // 创建输入数据
//        val inputData1 = FloatArray(480) { 0.5f }  // 示例输入数据1
        val inputData1 = ByteArray(480)  // 示例输入数据1

        val inputShape1 = longArrayOf(480)  // 形状1

        val inputData2 = FloatArray(45304) { 0.1f }  // 示例输入数据2
        val inputShape2 = longArrayOf(45304)  // 形状2

        val inputData3 = FloatArray(1) { 0.1f }  // 示例输入数据2
        val inputShape3 = longArrayOf(1)  // 形状2

        // 输入映射
        val inputDataMap = mapOf(
            "input_frame" to (inputData1 to inputShape1),
            "states" to (inputData2 to inputShape2),
            "atten_lim_db" to (inputData3 to inputShape3)
        )

        runInference(inputDataMap)
        Log.d(TAG, "inference complete")
        release()


//        input_names = ["input_frame", "states", "atten_lim_db"]
//        output_names = ["enhanced_audio_frame", "new_states", "lsnr"]
    }

    // 进行模型推理
    fun runInference(inputDataMap: Map<String, Pair<Any, LongArray>>): Map<String, Any>? {
        return try {

//            inputDataMap.forEach { (name, value) ->
//                Log.d(TAG, "Input Name: $name, Input Value: ${value.toString()}")
//            }

            val startTime = System.currentTimeMillis()

            val inputMap = inputDataMap.mapValues {
                createInputTensor(it.value.first, it.value.second)
            }

//            inputMap.forEach { (name, value) ->
//                Log.d(TAG, "Input Name: $name, Input Value: ${value}")
//            }

            val result = session.run(inputMap)
            val endTime = System.currentTimeMillis()

            val inferenceTime = (endTime - startTime) / 1000.0 // 以秒为单位
            val rtf = inferenceTime // 输入时间为1秒，所以RTF = 推理时间

            Log.d(TAG, "Inference Time: $inferenceTime seconds")
//            Log.d(TAG, "RTF: $rtf")


            // 获取输出结果
            val outputMap = result.associate { it.key to it.value }
//            outputMap.forEach { (name, value) ->
//                Log.d(TAG, "Output Name: $name, Output Value: ${value.toString()}")
//            }
//            result.close()
            // 返回输出
            outputMap
        } catch (e: OrtException) {
            Log.e(TAG, "Error during inference: ${e.message}")
            null
        }
    }


    fun runInference2(inputMap: Map<String, OnnxTensor>): Map<String, Any>? {
        return try {

//            inputDataMap.forEach { (name, value) ->
//                Log.d(TAG, "Input Name: $name, Input Value: ${value.toString()}")
//            }

            val startTime = System.currentTimeMillis()

//            val inputMap = inputDataMap.mapValues {
//                createInputTensor(it.value.first, it.value.second)
//            }

//            inputMap.forEach { (name, value) ->
//                Log.d(TAG, "Input Name: $name, Input Value: ${value}")
//            }

            val result = session.run(inputMap)
            val endTime = System.currentTimeMillis()

            val inferenceTime = (endTime - startTime) / 1000.0 // 以秒为单位
            val rtf = inferenceTime // 输入时间为1秒，所以RTF = 推理时间

            Log.d(TAG, "Inference Time: $inferenceTime seconds")
//            Log.d(TAG, "RTF: $rtf")


            // 获取输出结果
            val outputMap = result.associate { it.key to it.value }
//            outputMap.forEach { (name, value) ->
//                Log.d(TAG, "Output Name: $name, Output Value: ${value.toString()}")
//            }
//            result.close()
            // 返回输出
            outputMap
        } catch (e: OrtException) {
            Log.e(TAG, "Error during inference: ${e.message}")
            null
        }
    }

    // 释放资源
    fun release() {
        session.close()
        env.close()
    }

}