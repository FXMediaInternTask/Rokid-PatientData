package com.fxMedia.vadPatientDataAssistantAndroid.service.stt

/**
 * Result of STT credential validation
 */
sealed class SttValidationResult {
    object Valid : SttValidationResult()
    data class Invalid(val error: SttValidationError) : SttValidationResult()
}

/**
 * Validation error types
 */
enum class SttValidationError {
    INVALID_CREDENTIALS,
    NETWORK_ERROR,
    TIMEOUT,
    WRONG_ENDPOINT_OR_REGION,
    QUOTA_EXCEEDED,
    SERVICE_UNAVAILABLE,
    UNKNOWN
}

/**
 * Map HTTP status code to SttValidationError
 */
fun mapHttpStatusToError(code: Int): SttValidationError {
    return when (code) {
        401, 403 -> SttValidationError.INVALID_CREDENTIALS
        404 -> SttValidationError.WRONG_ENDPOINT_OR_REGION
        429 -> SttValidationError.QUOTA_EXCEEDED
        500, 502, 503, 504 -> SttValidationError.SERVICE_UNAVAILABLE
        else -> SttValidationError.UNKNOWN
    }
}
