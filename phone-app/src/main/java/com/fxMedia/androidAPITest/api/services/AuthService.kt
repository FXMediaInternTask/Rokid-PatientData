package com.fxMedia.androidAPITest.api.services

import com.fxMedia.androidAPITest.api.model.LoginRequest
import com.fxMedia.androidAPITest.api.model.LoginResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface AuthService {
    @FormUrlEncoded
    @POST("api/auth")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): LoginResponse

    @FormUrlEncoded
    @POST("api/auth/token")
    suspend fun validateToken(@Field("token") token: String): LoginResponse
}
