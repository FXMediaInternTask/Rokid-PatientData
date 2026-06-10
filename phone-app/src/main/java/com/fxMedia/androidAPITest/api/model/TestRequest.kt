package com.fxMedia.androidAPITest.api.model

import com.google.gson.annotations.SerializedName

data class TestRequest(
    @SerializedName("module_key")
    val moduleKey: String = "bot-1",
    @SerializedName("message")
    val message: String,
    @SerializedName("session_id")
    val sessionId: String? = null
)
