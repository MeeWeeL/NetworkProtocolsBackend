package ru.meeweel.network_protocols_backend.domain.large_read

import kotlinx.serialization.Serializable

@Serializable
data class LargeReadAttachment(
    val attachmentId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val checksum: String,
    val sourceSystem: String,
)
