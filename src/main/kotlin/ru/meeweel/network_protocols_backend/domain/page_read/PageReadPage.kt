package ru.meeweel.network_protocols_backend.domain.page_read

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadPreview

@Serializable
data class PageReadPage(
    val pageNumber: Int,
    val pageSize: Int,
    val totalItems: Int,
    val nextCursor: String?,
    val sortBy: String,
    val appliedFilters: List<String> = emptyList(),
    val summary: PageReadSummary,
    val facets: List<PageReadFacet> = emptyList(),
    val items: List<LargeReadPreview> = emptyList(),
)
