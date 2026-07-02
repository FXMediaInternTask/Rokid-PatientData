package com.fxMedia.patientDataAssistant.api.services

import com.fxMedia.patientDataAssistant.api.model.LoginRequest
import com.fxMedia.patientDataAssistant.api.model.LoginResponse
import com.fxMedia.patientDataAssistant.api.model.ValidateTokenRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("api/auth")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/token")
    suspend fun validateToken(@Body request: ValidateTokenRequest): LoginResponse
}
