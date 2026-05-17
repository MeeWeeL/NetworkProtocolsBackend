package ru.meeweel.network_protocols_backend.protocol

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_backend.domain.ErrorResponse
import ru.meeweel.network_protocols_backend.domain.ScenarioResponse
import ru.meeweel.network_protocols_backend.domain.ScenarioType

@Serializable
data class SocketServerEvent(
    val name: String,
    val scenario: ScenarioType? = null,
    val response: ScenarioResponse? = null,
    val error: ErrorResponse? = null,
    val message: String? = null,
)
