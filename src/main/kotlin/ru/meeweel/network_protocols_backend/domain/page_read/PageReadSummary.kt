package ru.meeweel.network_protocols_backend.domain.page_read

import kotlinx.serialization.Serializable

@Serializable
data class PageReadSummary(
    val totalAmount: Double,
    val selectedCount: Int,
    val highPriorityCount: Int,
    val staleCount: Int,
    val warningCount: Int,
)
