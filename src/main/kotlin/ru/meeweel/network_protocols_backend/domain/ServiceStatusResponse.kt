package ru.meeweel.network_protocols_backend.domain

import kotlinx.serialization.Serializable

@Serializable
data class ServiceStatusResponse(
    val status: String,
    val service: String? = null,
    val transport: List<String> = emptyList(),
    val message: String? = null,
)
