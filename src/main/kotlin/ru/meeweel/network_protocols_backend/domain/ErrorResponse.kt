package ru.meeweel.network_protocols_backend.domain

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val correlationId: String,
    val code: ErrorCode,
    val message: String,
    val transport: TransportType? = null,
    val scenario: ScenarioType? = null,
)
