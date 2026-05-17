package ru.meeweel.network_protocols_backend.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class SocketEnvelopeType {
    CLIENT_COMMAND,
    SERVER_EVENT,
}
