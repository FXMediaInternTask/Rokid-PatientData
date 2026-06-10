package com.fxMedia.androidAPITest.api.services

import com.fxMedia.androidAPITest.api.model.LoginRequest
import com.fxMedia.androidAPITest.api.model.LoginResponse
import com.fxMedia.androidAPITest.api.model.ValidateTokenRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("api/auth")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/auth/token")
    suspend fun validateToken(@Body request: ValidateTokenRequest): LoginResponse
}
