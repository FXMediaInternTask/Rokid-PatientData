package com.fxMedia.patientDataAssistant.api

import com.fxMedia.patientDataAssistant.api.services.AuthService
import com.fxMedia.patientDataAssistant.api.services.TestService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val DEV_BASE_URL = "https://dev-rokid.fxwebapps.com/"

    private val retrofit: Retrofit by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(DEV_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authService: AuthService by lazy {
        retrofit.create(AuthService::class.java)
    }

    val testInstance: TestService by lazy {
        retrofit.create(TestService::class.java)
    }
}
