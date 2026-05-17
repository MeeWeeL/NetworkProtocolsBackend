package ru.meeweel.network_protocols_backend.transport

import kotlinx.serialization.Serializable

@Serializable
data class GraphQlError(
    val message: String,
    val code: String? = null,
)
