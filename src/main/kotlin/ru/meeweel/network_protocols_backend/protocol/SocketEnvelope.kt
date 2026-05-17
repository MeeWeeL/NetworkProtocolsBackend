package ru.meeweel.network_protocols_backend.protocol

import kotlinx.serialization.Serializable

@Serializable
data class SocketEnvelope(
    val type: SocketEnvelopeType,
    val command: SocketClientCommand? = null,
    val event: SocketServerEvent? = null,
)
