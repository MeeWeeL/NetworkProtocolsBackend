package ru.meeweel.network_protocols_backend.domain

import kotlinx.serialization.Serializable
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadDocument
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadPreview
import ru.meeweel.network_protocols_backend.domain.page_read.PageReadPage
import ru.meeweel.network_protocols_backend.domain.stream_event.StreamEvent

/**
 * Единый ответ backend для всех протоколов.
 *
 * Клиент получает одинаковые поля независимо от транспорта. Поэтому дальше
 * раннер может считать задержку, checksum и число событий по одной схеме,
 * а не писать отдельную статистику для каждого протокола.
 */
@Serializable
data class ScenarioResponse(
    val requestId: String,
    val correlationId: String,
    val sessionId: String? = null,
    val scenario: ScenarioType,
    val transport: TransportType,
    val canonicalOperation: String,
    val status: String,
    val payloadSizeBytes: Int,
    val payloadChecksum: String,
    val sequence: Int? = null,
    val acceptedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val serverProcessingTimeMs: Long,
    val serverProcessingTimeMicros: Long,
    val payload: String? = null,
    val document: LargeReadDocument? = null,
    val preview: LargeReadPreview? = null,
    val page: PageReadPage? = null,
    val streamEvent: StreamEvent? = null,
    val metadata: Map<String, String> = emptyMap(),
)
