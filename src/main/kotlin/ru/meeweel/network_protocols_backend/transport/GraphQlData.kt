package ru.meeweel.network_protocols_backend.transport

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_backend.domain.ScenarioResponse

@Serializable
data class GraphQlData(
    val scenario: ScenarioResponse? = null,
    val executeScenario: ScenarioResponse? = null,
    val subscribeScenario: List<ScenarioResponse>? = null,
)
