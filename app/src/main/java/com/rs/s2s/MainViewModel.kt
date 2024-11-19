package com.rs.s2s

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.preference.PreferenceManager
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject


/**
 * description
 * @author whs
 * @date 2024/10/31
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val client = (application as MyApplication).okHttpClient
    private val preferences = PreferenceManager.getDefaultSharedPreferences(application)
    fun getServerUrl() = preferences.getString(PREFERENCES_KEY_URL, URL) as String
    fun getApiKey() = preferences.getString(PREFERENCES_KEY_KEY, API_KEY) as String
    fun getModelName() = preferences.getString(PREFERENCES_MODEL, MODEL) as String

    fun sendDeepSeekRequest(content: String, callback: Callback) {

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "You are a helpful assistant.")
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", content)
            })
        }
        val json = JSONObject().apply {
            put("model", MODEL)
            put("messages", messages)
            put("stream", false)
        }
        println(json.toString())
        val mediaType = "application/json".toMediaTypeOrNull()
        val body = json.toString().toRequestBody(mediaType)
        println(body)
        var apkKey = getApiKey()
        val request = Request.Builder()
            .url(getServerUrl())
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apkKey")
            .post(body)
            .build()

        // 使用传入的 OkHttpClient 发送请求
        client.newCall(request).enqueue(callback)
    }

    fun setSavedConfig(url: String,apikey: String, model:String) {
        preferences.edit {
            putString(PREFERENCES_KEY_URL, url)
            putString(PREFERENCES_KEY_KEY, apikey)
            putString(PREFERENCES_MODEL, model)

        }
    }


    companion object {
        private const val PREFERENCES_KEY_URL = "url"
        private const val PREFERENCES_KEY_KEY = "apikey"
        private const val PREFERENCES_MODEL = "model"
        const val URL = "https://api.deepseek.com/chat/completions" //https://api.openai.com/v1/chat/completions
        const val API_KEY = ""
        const val MODEL = "deepseek-chat" //deepseek-chat
    }


}