package ru.meeweel.network_protocols_backend.domain.page_read

import kotlinx.serialization.Serializable

@Serializable
data class PageReadFacet(
    val name: String,
    val title: String,
    val buckets: List<PageReadFacetBucket> = emptyList(),
)
