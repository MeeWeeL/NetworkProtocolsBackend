package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadParameter(
    val key: String,
    val title: String,
    val valueType: String,
    val value: String,
    val unit: String? = null,
    val required: Boolean,
    val source: String,
)
