package com.fxMedia.patientDataAssistant.api.model

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("status")
    val status: Boolean,
    @SerializedName("data")
    val data: UserData?,
    @SerializedName("token")
    val token: String?
)

data class UserData(
    @SerializedName("_id") val id: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("email") val email: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("role") val role: String?,
    @SerializedName("role_label") val roleLabel: String?,
    @SerializedName("staff_id") val staffId: String?,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("profile_picture") val profilePicture: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("about") val about: String?,
    @SerializedName("experience") val experience: String?,
    @SerializedName("last_login") val lastLogin: String?
)
