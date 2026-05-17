package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadDocument(
    val documentId: String,
    val externalId: String,
    val revision: Int,
    val generatedAtEpochMs: Long,
    val locale: String,
    val currency: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val status: String,
    val owner: LargeReadParty,
    val contacts: List<LargeReadContact> = emptyList(),
    val tags: List<String> = emptyList(),
    val flags: List<String> = emptyList(),
    val attributes: List<LargeReadAttribute> = emptyList(),
    val parameterGroups: List<LargeReadParameterGroup> = emptyList(),
    val lineItems: List<LargeReadLineItem> = emptyList(),
    val relatedEntities: List<LargeReadRelatedEntity> = emptyList(),
    val attachments: List<LargeReadAttachment> = emptyList(),
    val timeline: List<LargeReadTimelineEntry> = emptyList(),
    val metrics: LargeReadMetrics,
    val notes: List<String> = emptyList(),
    val narrative: String,
)
