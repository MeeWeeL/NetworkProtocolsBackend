package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadTimelineEntry(
    val eventCode: String,
    val title: String,
    val actor: String,
    val occurredAtEpochMs: Long,
    val status: String,
    val description: String,
)
