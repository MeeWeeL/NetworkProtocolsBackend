package ru.meeweel.network_protocols_backend.transport

import kotlinx.serialization.Serializable

@Serializable
data class GraphQlExtensions(
    val adapter: String,
    val correlationId: String,
    val code: String? = null,
    val scenario: String? = null,
)
