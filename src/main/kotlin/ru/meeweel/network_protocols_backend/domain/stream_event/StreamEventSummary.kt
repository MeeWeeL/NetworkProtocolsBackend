package ru.meeweel.network_protocols_backend.domain.stream_event

import kotlinx.serialization.Serializable

@Serializable
data class StreamEventSummary(
    val impactedItems: Int,
    val warningCount: Int,
    val scoreDelta: Double,
    val currentStatus: String,
)
