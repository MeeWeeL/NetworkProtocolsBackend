package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadContact(
    val kind: String,
    val label: String,
    val value: String,
    val preferred: Boolean,
    val availability: String,
)
