package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadParty(
    val partyId: String,
    val displayName: String,
    val role: String,
    val organization: String,
    val segment: String,
    val rating: Double,
)
