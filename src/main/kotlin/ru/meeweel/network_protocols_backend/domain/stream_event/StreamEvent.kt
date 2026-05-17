package ru.meeweel.network_protocols_backend.domain.stream_event

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadPreview

@Serializable
data class StreamEvent(
    val eventId: String,
    val eventType: String,
    val documentId: String,
    val emittedAtEpochMs: Long,
    val revision: Int,
    val priority: String,
    val preview: LargeReadPreview,
    val changedFields: List<String> = emptyList(),
    val relatedItems: List<LargeReadPreview> = emptyList(),
    val tags: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val summary: StreamEventSummary,
)
