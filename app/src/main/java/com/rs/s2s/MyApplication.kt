package com.rs.s2s

import android.app.Application
import android.content.Context
import okhttp3.OkHttpClient

/**
 * description
 * @author whs
 * @date 2024/9/24
 */
class MyApplication : Application() {
    lateinit var okHttpClient: OkHttpClient

    companion object {
        private var instance: MyApplication? = null

        fun getAppContext(): Context {
            return instance!!.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 初始化 OkHttpClient
        okHttpClient = OkHttpClient.Builder()
            .build()
    }
}