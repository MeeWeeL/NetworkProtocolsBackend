package ru.meeweel.network_protocols_backend.domain

import kotlinx.serialization.Serializable

@Serializable
enum class ErrorCode {
    VALIDATION,
    TIMEOUT,
    UNAVAILABLE,
    BUSINESS_CONFLICT,
    INTERNAL,
}
