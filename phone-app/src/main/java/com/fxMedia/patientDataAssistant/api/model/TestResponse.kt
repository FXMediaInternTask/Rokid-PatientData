package com.fxMedia.patientDataAssistant.api.model

import com.google.gson.annotations.SerializedName

data class TestResponse(
    @SerializedName("status")
    val status: Boolean,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: ChatResponseData?
)

data class ChatResponseData(
    @SerializedName("session_id")
    val sessionId: String?,
    @SerializedName("reply")
    val reply: String?,
    @SerializedName("history")
    val history: List<ChatHistory>?,
    @SerializedName("suggestions")
    val suggestions: List<String>?
)

data class ChatHistory(
    @SerializedName("role")
    val role: String,
    @SerializedName("content")
    val content: String,
    @SerializedName("createdAt")
    val createdAt: String
)
