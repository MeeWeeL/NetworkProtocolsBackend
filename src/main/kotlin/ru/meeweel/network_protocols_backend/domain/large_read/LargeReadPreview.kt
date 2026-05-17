package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadPreview(
    val documentId: String,
    val title: String,
    val status: String,
    val primaryBadge: String,
    val summaryScore: Double,
)
