package ru.meeweel.network_protocols_backend.domain

import kotlinx.serialization.Serializable

/**
 * Названия транспортов, которые сравниваются в стенде.
 */
@Serializable
enum class TransportType {
    REST,
    SOAP,
    GRAPHQL,
    WEBSOCKET,
    GRPC,
}
