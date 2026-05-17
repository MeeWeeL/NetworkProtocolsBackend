package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadAttribute(
    val code: String,
    val name: String,
    val value: String,
    val unit: String? = null,
    val category: String,
    val searchable: Boolean,
)
