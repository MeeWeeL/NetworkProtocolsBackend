package ru.meeweel.network_protocols_backend.transport

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GraphQlRequest(
    val query: String,
    val operationName: String? = null,
    val variables: JsonObject = JsonObject(emptyMap()),
)
