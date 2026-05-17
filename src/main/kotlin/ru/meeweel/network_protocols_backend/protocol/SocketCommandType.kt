package ru.meeweel.network_protocols_backend.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class SocketCommandType {
    START_SCENARIO,
    HEARTBEAT,
    CLOSE_SESSION,
}
