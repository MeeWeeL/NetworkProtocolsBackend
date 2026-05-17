package ru.meeweel.network_protocols_backend.transport

import ru.meeweel.network_protocols_backend.domain.ScenarioRequest
import ru.meeweel.network_protocols_backend.domain.ScenarioType

internal data class SoapScenarioCommand(
    val scenario: ScenarioType,
    val request: ScenarioRequest,
    val correlationId: String,
)
