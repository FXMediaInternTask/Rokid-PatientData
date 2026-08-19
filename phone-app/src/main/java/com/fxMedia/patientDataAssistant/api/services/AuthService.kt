package com.fxMedia.patientDataAssistant.api.services

import com.fxMedia.patientDataAssistant.api.model.LoginRequest
import com.fxMedia.patientDataAssistant.api.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AuthService {
    @POST("api/auth")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @FormUrlEncoded
    @POST("api/auth/token")
    suspend fun validateToken(@Field("token") token: String): LoginResponse
}
