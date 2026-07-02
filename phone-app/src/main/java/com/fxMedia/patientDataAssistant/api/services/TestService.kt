package com.fxMedia.patientDataAssistant.api.services

import com.fxMedia.patientDataAssistant.api.model.TestRequest
import com.fxMedia.patientDataAssistant.api.model.TestResponse
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
