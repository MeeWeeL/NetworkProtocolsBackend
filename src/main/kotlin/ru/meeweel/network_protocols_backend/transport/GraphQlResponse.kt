package ru.meeweel.network_protocols_backend.transport

import kotlinx.serialization.Serializable

@Serializable
data class GraphQlResponse(
    val data: GraphQlData? = null,
    val errors: List<GraphQlError> = emptyList(),
    val extensions: GraphQlExtensions,
)
