package com.fxMedia.patientDataAssistant.api.model

import com.google.gson.annotations.SerializedName

data class ValidateTokenRequest(
    @SerializedName("token")
    val token: String
)
