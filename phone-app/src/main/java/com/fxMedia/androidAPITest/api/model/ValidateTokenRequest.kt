package com.fxMedia.androidAPITest.api.model

import com.google.gson.annotations.SerializedName

data class ValidateTokenRequest(
    @SerializedName("token")
    val token: String
)
