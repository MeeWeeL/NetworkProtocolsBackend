package ru.meeweel.network_protocols_backend.domain

import kotlinx.serialization.Serializable

@Serializable
data class ScenarioCatalogItem(
    val scenario: ScenarioType,
    val canonicalOperation: String,
    val supportedTransports: List<TransportType>,
    val description: String,
)
