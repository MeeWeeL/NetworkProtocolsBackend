package ru.meeweel.network_protocols_backend.protocol

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_backend.domain.ScenarioRequest
import ru.meeweel.network_protocols_backend.domain.ScenarioType

@Serializable
data class SocketClientCommand(
    val command: SocketCommandType,
    val scenario: ScenarioType? = null,
    val request: ScenarioRequest? = null,
)
