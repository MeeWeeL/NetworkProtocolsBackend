package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadMetrics(
    val summaryScore: Double,
    val riskScore: Double,
    val completenessPct: Double,
    val freshnessHours: Double,
    val responseItems: Int,
    val attachmentBytes: Long,
    val warnings: Int,
)
