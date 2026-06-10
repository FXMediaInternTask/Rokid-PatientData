package com.fxMedia.androidAPITest.api.services

import com.fxMedia.androidAPITest.api.model.TestRequest
import com.fxMedia.androidAPITest.api.model.TestResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TestService {
    @POST("api/chatbot/chat")
    suspend fun sendTest(
        @Header("Authorization") token: String,
        @Body request: TestRequest
    ): TestResponse
}
