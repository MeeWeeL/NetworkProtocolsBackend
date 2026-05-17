package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadLineItem(
    val itemId: String,
    val sku: String,
    val title: String,
    val category: String,
    val quantity: Int,
    val unit: String,
    val unitPrice: Double,
    val totalPrice: Double,
    val availabilityStatus: String,
    val tags: List<String> = emptyList(),
)
