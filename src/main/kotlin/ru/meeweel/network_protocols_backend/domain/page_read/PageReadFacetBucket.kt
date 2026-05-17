package ru.meeweel.network_protocols_backend.domain.page_read

import kotlinx.serialization.Serializable

@Serializable
data class PageReadFacetBucket(
    val value: String,
    val count: Int,
    val selected: Boolean,
)
