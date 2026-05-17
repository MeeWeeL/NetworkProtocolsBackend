package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadParameterGroup(
    val groupCode: String,
    val groupTitle: String,
    val editable: Boolean,
    val parameters: List<LargeReadParameter> = emptyList(),
)
