package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadRelatedEntity(
    val entityId: String,
    val relationType: String,
    val title: String,
    val status: String,
    val priority: String,
)
