package com.rs.webrtc_apm

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
/**
 * description
 * @author whs
 * @date 2024/9/24
 */

open class WavFileWriter(
    private val sampleRate: Int,
    private val numChannels: Int,
    private val bitsPerSample: Int
) {

    private val TAG: String = WavFileWriter::class.simpleName.toString()
    private var fileOutputStream: FileOutputStream? = null
//    private var totalAudioLen: Long = 0

    // WAV 文件的 Chunk Size 为 36 + Subchunk2 Size
//    private val headerSize = 44
    private var curfile:File ? = null

    /**
     * 创建 WAV 文件并写入文件头
     */
    @Throws(IOException::class)
    fun createWavFile(file: File) {
        curfile = file
        fileOutputStream = FileOutputStream(file)
        writeWavHeader()
    }

    /**
     * 写入 PCM 数据（没有编码的音频数据）
     */
    @Throws(IOException::class)
    fun writePcmData(data: ByteArray) {
        fileOutputStream?.write(data)
//        totalAudioLen += data.size
    }

    /**
     * 关闭文件并更新 WAV 文件头中的文件长度
     */
    @Throws(IOException::class)
    fun closeWavFile() {
        fileOutputStream?.close()
        updateWavHeader()
    }

    /**
     * 写 WAV 文件头，填充默认信息
     */
    @Throws(IOException::class)
    private fun writeWavHeader() {
        Log.d(TAG,"writeWavHeader")

//        val header = ByteArray(headerSize)
//
//        // RIFF chunk descriptor
//        header[0] = 'R'.code.toByte()
//        header[1] = 'I'.code.toByte()
//        header[2] = 'F'.code.toByte()
//        header[3] = 'F'.code.toByte()
//
//        // ChunkSize (文件总大小 - 8)，占位符稍后会更新
//        val chunkSizePlaceholder = 0
//        writeIntToByteArray(header, 4, chunkSizePlaceholder)
//
//        // Format
//        header[8] = 'W'.code.toByte()
//        header[9] = 'A'.code.toByte()
//        header[10] = 'V'.code.toByte()
//        header[11] = 'E'.code.toByte()
//
//        // fmt sub-chunk
//        header[12] = 'f'.code.toByte()
//        header[13] = 'm'.code.toByte()
//        header[14] = 't'.code.toByte()
//        header[15] = ' '.code.toByte()
//
//        // Subchunk1Size (PCM = 16)
//        writeIntToByteArray(header, 16, 16)
//
//        // AudioFormat (PCM = 1)
//        writeShortToByteArray(header, 20, 1)
//
//        // NumChannels
//        writeShortToByteArray(header, 22, numChannels.toShort())
//
//        // SampleRate
//        writeIntToByteArray(header, 24, sampleRate)
//
//        // ByteRate (SampleRate * NumChannels * BitsPerSample / 8)
//        val byteRate = sampleRate * numChannels * bitsPerSample / 8
//        writeIntToByteArray(header, 28, byteRate)
//
//        // BlockAlign (NumChannels * BitsPerSample / 8)
//        val blockAlign = numChannels * bitsPerSample / 8
//        writeShortToByteArray(header, 32, blockAlign.toShort())
//
//        // BitsPerSample
//        writeShortToByteArray(header, 34, bitsPerSample.toShort())
//
//        // data sub-chunk
//        header[36] = 'd'.code.toByte()
//        header[37] = 'a'.code.toByte()
//        header[38] = 't'.code.toByte()
//        header[39] = 'a'.code.toByte()
//
//        // Subchunk2Size (音频数据长度)，占位符稍后更新
//        val dataSizePlaceholder = 0
//        writeIntToByteArray(header, 40, dataSizePlaceholder)
//
//        fileOutputStream?.write(header, 0, header.size)


//        Log.d(TAG, "writeWavHeader")

        // 创建 WAV 文件头
        val header = ByteArray(44)
        val byteRate = sampleRate * numChannels * bitsPerSample / 8

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
        header[22] = numChannels.toByte()
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
        header[32] = ((numChannels * bitsPerSample) / 8).toByte()
        header[33] = 0

        // Bits Per Sample
        header[34] = bitsPerSample.toByte()
        header[35] = 0

        // Subchunk2 ID "data"
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()

        // Subchunk2 Size（音频数据的大小，先留空，最后补上）
        // header[40] 到 header[43] 留空，稍后补上

        // 将 header 写入文件
        fileOutputStream?.write(header, 0, 44)
    }

    /**
     * 更新 WAV 文件头以反映文件的实际大小
     */
    @Throws(IOException::class)
    private fun updateWavHeader() {
        Log.d(TAG,"updateWavHeader")
//        val fileSize = file.length()
//        val totalDataLen = fileSize - 8
//        val totalAudioLen = fileSize - headerSize
//
//        val raf = file.outputStream().channel
//        raf.position(4)
//        raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen.toInt()))
//
//        raf.position(40)
//        raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalAudioLen.toInt()))
//
//        raf.close()


        val wavFile = RandomAccessFile(curfile, "rw")
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

//    private fun writeIntToByteArray(data: ByteArray, offset: Int, value: Int) {
//        data[offset] = (value shr 0).toByte()
//        data[offset + 1] = (value shr 8).toByte()
//        data[offset + 2] = (value shr 16).toByte()
//        data[offset + 3] = (value shr 24).toByte()
//    }
//
//    private fun writeShortToByteArray(data: ByteArray, offset: Int, value: Short) {
//        data[offset] = (value.toInt() shr 0).toByte()
//        data[offset + 1] = (value.toInt() shr 8).toByte()
//    }
}