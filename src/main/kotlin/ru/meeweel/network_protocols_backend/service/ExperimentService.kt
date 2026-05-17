package ru.meeweel.network_protocols_backend.service

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.meeweel.network_protocols_backend.domain.ErrorCode
import ru.meeweel.network_protocols_backend.domain.FailureMode
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadAttachment
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadAttribute
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadContact
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadDocument
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadLineItem
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadMetrics
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadParameter
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadParameterGroup
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadParty
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadPreview
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadRelatedEntity
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadTimelineEntry
import ru.meeweel.network_protocols_backend.domain.page_read.PageReadFacet
import ru.meeweel.network_protocols_backend.domain.page_read.PageReadFacetBucket
import ru.meeweel.network_protocols_backend.domain.page_read.PageReadPage
import ru.meeweel.network_protocols_backend.domain.page_read.PageReadSummary
import ru.meeweel.network_protocols_backend.domain.ScenarioCatalogItem
import ru.meeweel.network_protocols_backend.domain.ScenarioRequest
import ru.meeweel.network_protocols_backend.domain.ScenarioResponse
import ru.meeweel.network_protocols_backend.domain.ScenarioType
import ru.meeweel.network_protocols_backend.domain.stream_event.StreamEvent
import ru.meeweel.network_protocols_backend.domain.stream_event.StreamEventSummary
import ru.meeweel.network_protocols_backend.domain.TransportType

/**
 * Единое прикладное ядро backend-стенда.
 *
 * Важно: REST, SOAP, GraphQL, WebSocket и gRPC приходят сюда разными дорогами,
 * но дальше выполняют одну и ту же бизнес-логику сценария. Благодаря этому
 * мы сравниваем протокольную обвязку, а не пять разных реализаций задачи.
 */
@OptIn(ExperimentalSerializationApi::class)
class ExperimentService {
    private val payloadJson = Json {
        prettyPrint = false
        encodeDefaults = true
        explicitNulls = false
    }

    fun scenarioCatalog(): List<ScenarioCatalogItem> {
        // Каталог нужен клиенту как "меню стенда": какие сценарии есть,
        // какая у них прикладная операция и какие транспорты разрешены.
        return ScenarioType.entries.map { scenario ->
            ScenarioCatalogItem(
                scenario = scenario,
                canonicalOperation = canonicalOperation(scenario),
                supportedTransports = supportedTransports(scenario),
                description = scenarioDescription(scenario),
            )
        }
    }

    suspend fun executeRest(
        scenario: ScenarioType,
        request: ScenarioRequest,
    ): ScenarioResponse {
        ensureTransportSupport(TransportType.REST, scenario)
        return execute(
            scenario = scenario,
            request = request,
            transport = TransportType.REST,
        )
    }

    suspend fun executeSoap(
        scenario: ScenarioType,
        request: ScenarioRequest,
    ): ScenarioResponse {
        ensureTransportSupport(TransportType.SOAP, scenario)
        return execute(
            scenario = scenario,
            request = request,
            transport = TransportType.SOAP,
        )
    }

    suspend fun executeGraphQl(
        scenario: ScenarioType,
        request: ScenarioRequest,
    ): ScenarioResponse {
        ensureTransportSupport(TransportType.GRAPHQL, scenario)
        return execute(
            scenario = scenario,
            request = request,
            transport = TransportType.GRAPHQL,
        )
    }

    suspend fun executeWebSocket(
        scenario: ScenarioType,
        request: ScenarioRequest,
        sequence: Int? = null,
    ): ScenarioResponse {
        ensureTransportSupport(TransportType.WEBSOCKET, scenario)
        return execute(
            scenario = scenario,
            request = request,
            transport = TransportType.WEBSOCKET,
            sequence = sequence,
        )
    }

    fun buildGraphQlStream(
        scenario: ScenarioType,
        request: ScenarioRequest,
    ): Flow<ScenarioResponse> = flow {
        ensureTransportSupport(TransportType.GRAPHQL, scenario)
        val events = request.eventCount.coerceIn(1, 100)
        val intervalMs = streamIntervalMs(request)
        val requestId = request.requestId ?: UUID.randomUUID().toString()
        val requestWithId = request.copy(
            requestId = requestId,
            correlationId = request.correlationId ?: requestId,
            sessionId = request.sessionId ?: UUID.randomUUID().toString(),
        )
        (1..events).forEach { sequence ->
            if (sequence > 1 && intervalMs > 0L) {
                delay(intervalMs)
            }
            emit(
                execute(
                    scenario = scenario,
                    request = requestWithId,
                    transport = TransportType.GRAPHQL,
                    sequence = sequence,
                ),
            )
        }
    }

    suspend fun executeGrpc(
        scenario: ScenarioType,
        request: ScenarioRequest,
        sequence: Int? = null,
    ): ScenarioResponse {
        ensureTransportSupport(TransportType.GRPC, scenario)
        return execute(
            scenario = scenario,
            request = request,
            transport = TransportType.GRPC,
            sequence = sequence,
        )
    }

    fun buildStream(
        scenario: ScenarioType,
        request: ScenarioRequest,
    ): Flow<ScenarioResponse> = flow {
        ensureTransportSupport(TransportType.WEBSOCKET, scenario)
        val events = request.eventCount.coerceIn(1, 100)
        val intervalMs = streamIntervalMs(request)
        val requestId = request.requestId ?: UUID.randomUUID().toString()
        val requestWithId = request.copy(
            requestId = requestId,
            correlationId = request.correlationId ?: requestId,
            sessionId = request.sessionId ?: UUID.randomUUID().toString(),
        )
        (1..events).forEach { sequence ->
            if (sequence > 1 && intervalMs > 0L) {
                delay(intervalMs)
            }
            emit(
                executeWebSocket(
                    scenario = scenario,
                    request = requestWithId,
                    sequence = sequence,
                ),
            )
        }
    }

    fun buildGrpcStream(
        scenario: ScenarioType,
        request: ScenarioRequest,
    ): Flow<ScenarioResponse> = flow {
        ensureTransportSupport(TransportType.GRPC, scenario)
        val events = request.eventCount.coerceIn(1, 100)
        val intervalMs = streamIntervalMs(request)
        val requestId = request.requestId ?: UUID.randomUUID().toString()
        val requestWithId = request.copy(
            requestId = requestId,
            correlationId = request.correlationId ?: requestId,
            sessionId = request.sessionId ?: UUID.randomUUID().toString(),
        )
        (1..events).forEach { sequence ->
            if (sequence > 1 && intervalMs > 0L) {
                delay(intervalMs)
            }
            emit(
                executeGrpc(
                    scenario = scenario,
                    request = requestWithId,
                    sequence = sequence,
                ),
            )
        }
    }

    private suspend fun execute(
        scenario: ScenarioType,
        request: ScenarioRequest,
        transport: TransportType,
        sequence: Int? = null,
    ): ScenarioResponse {
        val startedAtNanos = System.nanoTime()
        val requestId = request.requestId ?: UUID.randomUUID().toString()
        val correlationId = request.correlationId ?: requestId
        val sessionId = request.sessionId ?: if (scenario in streamAndSessionScenarios()) {
            UUID.randomUUID().toString()
        } else {
            null
        }
        val acceptedAt = System.currentTimeMillis()
        maybeFail(request.failureMode, scenario)
        val payloadSize = resolvePayloadSize(scenario, request)
        // Ниже выбирается полезная нагрузка для сценария.
        // S2 строит полный большой объект, S3 берет из него только короткую
        // карточку-превью, S4 строит страницу списка, S7-S8 - события потока.
        // Так разные протоколы делают одну и ту же прикладную работу.
        val sourceDocument = when (scenario) {
            ScenarioType.S2_LARGE_READ -> buildLargeReadDocument(
                requestId = requestId,
                correlationId = correlationId,
                targetSizeBytes = payloadSize,
                generatedAtEpochMs = acceptedAt,
            )

            ScenarioType.S3_PARTIAL_LARGE_READ -> buildLargeReadDocument(
                requestId = requestId,
                correlationId = correlationId,
                targetSizeBytes = 32_768,
                generatedAtEpochMs = acceptedAt,
            )

            else -> null
        }
        val preview = when (scenario) {
            ScenarioType.S3_PARTIAL_LARGE_READ -> sourceDocument?.toPreview()
            else -> null
        }
        val page = when (scenario) {
            ScenarioType.S4_PAGE_READ -> buildPageReadPage(
                requestId = requestId,
                targetSizeBytes = payloadSize,
            )

            else -> null
        }
        val streamEvent = when (scenario) {
            ScenarioType.S7_EVENT_STREAM -> buildStreamEvent(
                requestId = requestId,
                sequence = sequence ?: 1,
                targetSizeBytes = payloadSize,
                generatedAtEpochMs = acceptedAt,
                heavy = false,
            )

            ScenarioType.S8_HEAVY_EVENT_STREAM -> buildStreamEvent(
                requestId = requestId,
                sequence = sequence ?: 1,
                targetSizeBytes = payloadSize,
                generatedAtEpochMs = acceptedAt,
                heavy = true,
            )

            else -> null
        }
        val payload = when (scenario) {
            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
            -> request.payload ?: payloadOfSize(payloadSize)

            ScenarioType.S1_SHORT_READ,
            ScenarioType.S9_LONG_SESSION,
            -> payloadOfSize(payloadSize)

            ScenarioType.S2_LARGE_READ,
            ScenarioType.S3_PARTIAL_LARGE_READ,
            ScenarioType.S4_PAGE_READ,
            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            -> null
        }
        val canonicalPayload = when {
            sourceDocument != null && scenario == ScenarioType.S2_LARGE_READ ->
                payloadJson.encodeToString(sourceDocument)

            preview != null ->
                payloadJson.encodeToString(preview)

            page != null ->
                payloadJson.encodeToString(page)

            streamEvent != null ->
                payloadJson.encodeToString(streamEvent)

            else -> payload.orEmpty()
        }

        if (request.artificialDelayMs > 0) {
            delay(request.artificialDelayMs.coerceAtMost(5_000))
        }

        val completedAt = System.currentTimeMillis()
        // canonicalPayload - это "эталонный текст" ответа/события.
        // По его размеру и checksum клиент проверяет, что протоколы передали
        // сопоставимые данные, а не случайно разные по смыслу объекты.
        val serverProcessingTimeMicros = ((System.nanoTime() - startedAtNanos) / 1_000L)
            .coerceAtLeast(0L)
        return ScenarioResponse(
            requestId = requestId,
            correlationId = correlationId,
            sessionId = sessionId,
            scenario = scenario,
            transport = transport,
            canonicalOperation = canonicalOperation(scenario),
            status = "ok",
            payloadSizeBytes = canonicalPayload.toByteArray(StandardCharsets.UTF_8).size,
            payloadChecksum = checksum(canonicalPayload),
            sequence = sequence,
            acceptedAtEpochMs = acceptedAt,
            completedAtEpochMs = completedAt,
            serverProcessingTimeMs = serverProcessingTimeMicros / 1_000L,
            serverProcessingTimeMicros = serverProcessingTimeMicros,
            payload = when (scenario) {
                ScenarioType.S2_LARGE_READ,
                ScenarioType.S3_PARTIAL_LARGE_READ,
                ScenarioType.S4_PAGE_READ,
                ScenarioType.S5_SMALL_WRITE_ACK,
                ScenarioType.S6_LARGE_WRITE_ACK,
                ScenarioType.S7_EVENT_STREAM,
                ScenarioType.S8_HEAVY_EVENT_STREAM,
                -> null

                else -> payload
            },
            document = sourceDocument.takeIf { scenario == ScenarioType.S2_LARGE_READ },
            preview = preview,
            page = page,
            streamEvent = streamEvent,
            metadata = request.metadata + mapOf(
                "serverScenario" to scenario.name,
                "transport" to transport.name,
                "qClass" to (request.qClass ?: "unspecified"),
                "loadProfile" to (request.loadProfile ?: "unspecified"),
            ),
        )
    }

    private fun maybeFail(
        failureMode: FailureMode,
        scenario: ScenarioType,
    ) {
        when (failureMode) {
            FailureMode.NONE -> Unit
            FailureMode.VALIDATION -> throw BackendException(
                code = ErrorCode.VALIDATION,
                message = "Validation error for scenario ${scenario.name}",
            )

            FailureMode.TIMEOUT -> throw BackendException(
                code = ErrorCode.TIMEOUT,
                message = "Deadline exceeded for scenario ${scenario.name}",
            )

            FailureMode.UNAVAILABLE -> throw BackendException(
                code = ErrorCode.UNAVAILABLE,
                message = "Service is temporarily unavailable for scenario ${scenario.name}",
            )

            FailureMode.BUSINESS_CONFLICT -> throw BackendException(
                code = ErrorCode.BUSINESS_CONFLICT,
                message = "Business conflict for scenario ${scenario.name}",
            )

            FailureMode.INTERNAL -> throw BackendException(
                code = ErrorCode.INTERNAL,
                message = "Internal server error for scenario ${scenario.name}",
            )
        }
    }

    private fun resolvePayloadSize(
        scenario: ScenarioType,
        request: ScenarioRequest,
    ): Int {
        val requested = request.payloadSizeBytes ?: 0
        // Защита от случайных некорректных размеров. Если клиент попросит
        // слишком мало или слишком много, backend приводит значение к разумным
        // границам для конкретного сценария.
        return when (scenario) {
            ScenarioType.S1_SHORT_READ -> requested.coerceIn(64, 1_024)
            ScenarioType.S2_LARGE_READ -> requested.coerceIn(4_096, 256_000)
            ScenarioType.S3_PARTIAL_LARGE_READ -> requested.coerceIn(128, 4_096)
            ScenarioType.S4_PAGE_READ -> requested.coerceIn(2_048, 128_000)
            ScenarioType.S5_SMALL_WRITE_ACK -> (request.payload?.toByteArray(StandardCharsets.UTF_8)?.size ?: requested)
                .coerceIn(32, 4_096)
            ScenarioType.S6_LARGE_WRITE_ACK -> (request.payload?.toByteArray(StandardCharsets.UTF_8)?.size ?: requested)
                .coerceIn(32, 256_000)
            ScenarioType.S7_EVENT_STREAM -> requested.coerceIn(64, 8_192)
            ScenarioType.S8_HEAVY_EVENT_STREAM -> requested.coerceIn(1_024, 64_000)
            ScenarioType.S9_LONG_SESSION -> requested.coerceIn(32, 1_024)
        }
    }

    private fun payloadOfSize(size: Int): String {
        val template = "ktor-backend-payload-"
        val builder = StringBuilder(size)
        while (builder.length < size) {
            builder.append(template)
        }
        return builder.substring(0, size)
    }

    private fun checksum(payload: String): String {
        // Контрольная сумма - это как пломба на посылке: клиент может проверить,
        // что отправленное или полученное тело не изменилось по дороге.
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(payload.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun canonicalOperation(scenario: ScenarioType): String {
        return when (scenario) {
            ScenarioType.S1_SHORT_READ -> "readSmall"
            ScenarioType.S2_LARGE_READ -> "readLarge"
            ScenarioType.S3_PARTIAL_LARGE_READ -> "readLargePreview"
            ScenarioType.S4_PAGE_READ -> "readPage"
            ScenarioType.S5_SMALL_WRITE_ACK -> "writeSmallAck"
            ScenarioType.S6_LARGE_WRITE_ACK -> "writeLargeAck"
            ScenarioType.S7_EVENT_STREAM -> "eventStream"
            ScenarioType.S8_HEAVY_EVENT_STREAM -> "eventStreamHeavy"
            ScenarioType.S9_LONG_SESSION -> "sessionPulse"
        }
    }

    private fun scenarioDescription(scenario: ScenarioType): String {
        return when (scenario) {
            ScenarioType.S1_SHORT_READ -> "Короткое чтение с малым payload"
            ScenarioType.S2_LARGE_READ -> "Чтение полного большого структурированного объекта"
            ScenarioType.S3_PARTIAL_LARGE_READ -> "Чтение компактной проекции того же объекта под UI"
            ScenarioType.S4_PAGE_READ -> "Чтение страницы списка с курсором, фасетами и сводкой"
            ScenarioType.S5_SMALL_WRITE_ACK -> "Передача малого набора данных с подтверждением"
            ScenarioType.S6_LARGE_WRITE_ACK -> "Передача большого набора данных с подтверждением"
            ScenarioType.S7_EVENT_STREAM -> "Поток компактных событий / подписка"
            ScenarioType.S8_HEAVY_EVENT_STREAM -> "Поток больших структурированных событий / подписка"
            ScenarioType.S9_LONG_SESSION -> "Длительная сессия / служебные сигналы"
        }
    }

    private fun supportedTransports(scenario: ScenarioType): List<TransportType> {
        // Здесь зашито правило допустимости из методики.
        // Например, S9 оставлен только для WebSocket и gRPC, потому что это
        // естественные технологии для длительного канала.
        return when (scenario) {
            ScenarioType.S1_SHORT_READ,
            ScenarioType.S2_LARGE_READ,
            ScenarioType.S3_PARTIAL_LARGE_READ,
            ScenarioType.S4_PAGE_READ,
            ScenarioType.S5_SMALL_WRITE_ACK,
            ScenarioType.S6_LARGE_WRITE_ACK,
            -> listOf(
                TransportType.REST,
                TransportType.SOAP,
                TransportType.GRAPHQL,
                TransportType.WEBSOCKET,
                TransportType.GRPC,
            )

            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            -> listOf(
                TransportType.GRAPHQL,
                TransportType.WEBSOCKET,
                TransportType.GRPC,
            )

            ScenarioType.S9_LONG_SESSION ->
                listOf(
                    TransportType.WEBSOCKET,
                    TransportType.GRPC,
                )
        }
    }

    private fun ensureTransportSupport(
        transport: TransportType,
        scenario: ScenarioType,
    ) {
        if (transport !in supportedTransports(scenario)) {
            throw BackendException(
                code = ErrorCode.VALIDATION,
                message = "Сценарий ${scenario.name} не поддерживается транспортом ${transport.name}.",
            )
        }
    }

    private fun streamIntervalMs(request: ScenarioRequest): Long {
        return request.metadata["eventIntervalMs"]
            ?.toLongOrNull()
            ?.coerceIn(0L, 5_000L)
            ?: DEFAULT_STREAM_INTERVAL_MS
    }

    private fun buildLargeReadDocument(
        requestId: String,
        correlationId: String,
        targetSizeBytes: Int,
        generatedAtEpochMs: Long,
    ): LargeReadDocument {
        // Большой объект специально похож на реальную карточку из приложения:
        // контакты, параметры, позиции, связанные сущности, вложения и история.
        // Это нагружает сериализацию и разбор ответа сильнее, чем простая строка.
        val density = (targetSizeBytes / 4_096).coerceAtLeast(1)
        val contacts = MutableList((2 + density).coerceAtMost(8)) { index ->
            LargeReadContact(
                kind = listOf("owner", "support", "operations", "finance", "security")[index % 5],
                label = "Контакт ${index + 1}",
                value = "contact${index + 1}@demo.service.local",
                preferred = index == 0,
                availability = if (index % 2 == 0) "business-hours" else "24x7",
            )
        }
        val tags = MutableList(6 + density) { index ->
            listOf("mobile", "synchronization", "catalog", "billing", "android", "priority")[index % 6] +
                "-${index + 1}"
        }
        val flags = mutableListOf(
            "requires-approval",
            "contains-related-entities",
            "supports-partial-view",
            "has-audit-history",
        )
        val attributes = MutableList(12 + density * 4) { index ->
            LargeReadAttribute(
                code = "attr_${index + 1}",
                name = "Параметр ${index + 1}",
                value = "Значение ${index + 1} для составного клиентского документа",
                unit = when (index % 4) {
                    0 -> "ms"
                    1 -> "шт"
                    2 -> "%"
                    else -> null
                },
                category = listOf("general", "commercial", "routing", "limits")[index % 4],
                searchable = index % 3 != 0,
            )
        }
        val parameterGroups = MutableList((3 + density / 2).coerceAtMost(8)) { groupIndex ->
            LargeReadParameterGroup(
                groupCode = "grp_${groupIndex + 1}",
                groupTitle = "Группа параметров ${groupIndex + 1}",
                editable = groupIndex % 2 == 0,
                parameters = List((5 + density / 2).coerceAtMost(10)) { paramIndex ->
                    LargeReadParameter(
                        key = "param_${groupIndex + 1}_${paramIndex + 1}",
                        title = "Параметр ${groupIndex + 1}.${paramIndex + 1}",
                        valueType = listOf("string", "integer", "decimal", "enum")[paramIndex % 4],
                        value = when (paramIndex % 4) {
                            0 -> "value-${groupIndex + 1}-${paramIndex + 1}"
                            1 -> ((groupIndex + 1) * (paramIndex + 3) * 7).toString()
                            2 -> "%.2f".format((groupIndex + 1) * (paramIndex + 1) * 1.75)
                            else -> listOf("draft", "review", "approved", "archived")[paramIndex % 4]
                        },
                        unit = if (paramIndex % 3 == 0) "ms" else null,
                        required = paramIndex % 2 == 0,
                        source = listOf("server", "import", "operator", "calculated")[paramIndex % 4],
                    )
                },
            )
        }
        val lineItems = MutableList(4 + density * 2) { index ->
            val quantity = index + 1
            val unitPrice = 149.0 + index * 17.5
            LargeReadLineItem(
                itemId = "item-${index + 1}",
                sku = "SKU-${1000 + index}",
                title = "Позиция ${index + 1} клиентского представления",
                category = listOf("base", "option", "service", "analytics")[index % 4],
                quantity = quantity,
                unit = "шт",
                unitPrice = unitPrice,
                totalPrice = unitPrice * quantity,
                availabilityStatus = if (index % 3 == 0) "limited" else "available",
                tags = listOf("ui", "network", "batch-${index + 1}"),
            )
        }
        val relatedEntities = MutableList(3 + density) { index ->
            LargeReadRelatedEntity(
                entityId = "rel-${index + 1}",
                relationType = listOf("owner", "supplier", "policy", "history")[index % 4],
                title = "Связанная сущность ${index + 1}",
                status = listOf("active", "pending", "archived")[index % 3],
                priority = listOf("low", "medium", "high")[index % 3],
            )
        }
        val attachments = MutableList((2 + density).coerceAtMost(12)) { index ->
            LargeReadAttachment(
                attachmentId = "att-${index + 1}",
                fileName = "document_part_${index + 1}.json",
                mimeType = "application/json",
                sizeBytes = 12_000L + index * 1_750L,
                checksum = checksum("attachment-$requestId-$index"),
                sourceSystem = listOf("crm", "billing", "catalog", "archive")[index % 4],
            )
        }
        val timeline = MutableList(5 + density * 2) { index ->
            LargeReadTimelineEntry(
                eventCode = "EVT_${index + 1}",
                title = "Событие ${index + 1}",
                actor = listOf("system", "operator", "scheduler", "replica")[index % 4],
                occurredAtEpochMs = generatedAtEpochMs - (index + 1) * 3_600_000L,
                status = listOf("done", "queued", "verified")[index % 3],
                description = buildNarrativeSentence(index),
            )
        }
        val notes = mutableListOf<String>()
        repeat(4 + density) { index ->
            notes += buildNote(index)
        }
        var narrative = buildNarrative(4 + density)

        fun snapshot(): LargeReadDocument {
            return LargeReadDocument(
                documentId = "doc-$requestId",
                externalId = "ext-${correlationId.take(8)}-${targetSizeBytes}",
                revision = 3 + density,
                generatedAtEpochMs = generatedAtEpochMs,
                locale = "ru-RU",
                currency = "RUB",
                title = "Сводный документ параметров и связей мобильного клиента",
                subtitle = "Полный объект для сценариев чтения с различной степенью детализации",
                category = "client-composite-view",
                status = if (density >= 6) "expanded" else "standard",
                owner = LargeReadParty(
                    partyId = "owner-${correlationId.take(8)}",
                    displayName = "Сервис агрегированных представлений",
                    role = "data-owner",
                    organization = "MeeWeeL Demo Systems",
                    segment = "android",
                    rating = 4.6 + (density.coerceAtMost(5) * 0.05),
                ),
                contacts = contacts.toList(),
                tags = tags.toList(),
                flags = flags.toList(),
                attributes = attributes.toList(),
                parameterGroups = parameterGroups.toList(),
                lineItems = lineItems.toList(),
                relatedEntities = relatedEntities.toList(),
                attachments = attachments.toList(),
                timeline = timeline.toList(),
                metrics = LargeReadMetrics(
                    summaryScore = 82.0 + density,
                    riskScore = 18.0 + density * 1.5,
                    completenessPct = 91.0 + density.coerceAtMost(7) * 0.7,
                    freshnessHours = density / 2.0,
                    responseItems = lineItems.size + attributes.size + relatedEntities.size,
                    attachmentBytes = attachments.sumOf(LargeReadAttachment::sizeBytes),
                    warnings = density / 2,
                ),
                notes = notes.toList(),
                narrative = narrative,
            )
        }

        var document = snapshot()
        var encodedSize = payloadJson.encodeToString(document).toByteArray(StandardCharsets.UTF_8).size
        var index = 0
        // Добираем объект до нужного размера не пробелами, а осмысленными полями.
        // Так клиент разбирает нормальную структуру, а не искусственный мусор.
        while (encodedSize < targetSizeBytes) {
            when (index % 4) {
                0 -> {
                    notes += buildNote(notes.size)
                    tags += "detail-${tags.size + 1}"
                }

                1 -> attributes += LargeReadAttribute(
                    code = "extra_${attributes.size + 1}",
                    name = "Дополнительный параметр ${attributes.size + 1}",
                    value = "Расширенное описание параметра ${attributes.size + 1} для полного ответа мобильному клиенту",
                    unit = null,
                    category = "extended",
                    searchable = true,
                )

                2 -> lineItems += LargeReadLineItem(
                    itemId = "item-${lineItems.size + 1}",
                    sku = "SKU-${2000 + lineItems.size}",
                    title = "Дополнительная позиция ${lineItems.size + 1}",
                    category = "analytics",
                    quantity = lineItems.size + 1,
                    unit = "шт",
                    unitPrice = 199.0 + lineItems.size * 11.0,
                    totalPrice = (199.0 + lineItems.size * 11.0) * (lineItems.size + 1),
                    availabilityStatus = "available",
                    tags = listOf("extended", "ui", "detail"),
                )

                else -> narrative += " " + buildNarrativeSentence(index + density)
            }
            document = snapshot()
            encodedSize = payloadJson.encodeToString(document).toByteArray(StandardCharsets.UTF_8).size
            index += 1
        }
        return document
    }

    private fun LargeReadDocument.toPreview(): LargeReadPreview {
        return LargeReadPreview(
            documentId = documentId,
            title = title,
            status = status,
            primaryBadge = tags.firstOrNull().orEmpty(),
            summaryScore = metrics.summaryScore,
        )
    }

    private fun buildPageReadPage(
        requestId: String,
        targetSizeBytes: Int,
    ): PageReadPage {
        // S4 имитирует типичный экран со списком: элементы, курсор следующей
        // страницы, фильтры, фасеты и сводка. Это ближе к мобильному UI, чем
        // просто массив одинаковых строк.
        val density = (targetSizeBytes / 4_096).coerceAtLeast(1)
        val pageSize = (12 + density * 4).coerceAtMost(48)
        val previews = MutableList(pageSize) { index ->
            previewForIndex(
                requestId = requestId,
                index = index,
                suffix = "page",
            )
        }
        val appliedFilters = mutableListOf(
            "status:active",
            "segment:android",
            "priority:high",
        )
        val facets = mutableListOf(
            PageReadFacet(
                name = "status",
                title = "Статус",
                buckets = listOf(
                    PageReadFacetBucket(value = "active", count = 186, selected = true),
                    PageReadFacetBucket(value = "pending", count = 41, selected = false),
                    PageReadFacetBucket(value = "archived", count = 12, selected = false),
                ),
            ),
            PageReadFacet(
                name = "segment",
                title = "Сегмент",
                buckets = listOf(
                    PageReadFacetBucket(value = "android", count = 122, selected = true),
                    PageReadFacetBucket(value = "ios", count = 88, selected = false),
                    PageReadFacetBucket(value = "shared", count = 29, selected = false),
                ),
            ),
        )

        fun snapshot(): PageReadPage {
            val highPriorityCount = previews.count { it.primaryBadge.contains("priority", ignoreCase = true) }
            return PageReadPage(
                pageNumber = 1,
                pageSize = previews.size,
                totalItems = previews.size * (8 + density),
                nextCursor = "cursor-${requestId.take(8)}-${previews.size}",
                sortBy = "updatedAt:desc",
                appliedFilters = appliedFilters.toList(),
                summary = PageReadSummary(
                    totalAmount = previews.size * 12_450.0,
                    selectedCount = previews.size,
                    highPriorityCount = highPriorityCount,
                    staleCount = previews.count { it.status == "stale" },
                    warningCount = previews.count { it.status == "limited" },
                ),
                facets = facets.toList(),
                items = previews.toList(),
            )
        }

        var page = snapshot()
        var encodedSize = payloadJson.encodeToString(page).toByteArray(StandardCharsets.UTF_8).size
        var index = previews.size
        while (encodedSize < targetSizeBytes) {
            when (index % 3) {
                0 -> previews += previewForIndex(requestId, index, "page")
                1 -> facets += PageReadFacet(
                    name = "channel-$index",
                    title = "Канал ${index + 1}",
                    buckets = listOf(
                        PageReadFacetBucket(value = "direct", count = 12 + index, selected = false),
                        PageReadFacetBucket(value = "partner", count = 7 + index, selected = false),
                    ),
                )

                else -> appliedFilters += "flag:detail-${index + 1}"
            }
            page = snapshot()
            encodedSize = payloadJson.encodeToString(page).toByteArray(StandardCharsets.UTF_8).size
            index += 1
        }
        return page
    }

    private fun buildStreamEvent(
        requestId: String,
        sequence: Int,
        targetSizeBytes: Int,
        generatedAtEpochMs: Long,
        heavy: Boolean,
    ): StreamEvent {
        // Событие потока - это одно обновление, которое прилетает клиенту.
        // Легкий поток S7 несет компактную дельту, тяжелый S8 - более плотный
        // снимок данных, чтобы проверить стоимость обработки больших событий.
        val density = (targetSizeBytes / if (heavy) 2_048 else 4_096).coerceAtLeast(1)
        val changedFields = mutableListOf(
            "status",
            "summaryScore",
            "updatedAt",
            "availability",
        )
        val relatedItems = mutableListOf(
            previewForIndex(requestId, sequence, "stream"),
        )
        val tags = mutableListOf(
            if (heavy) "heavy-stream" else "light-stream",
            "sequence-${sequence}",
            "android",
        )
        val notes = mutableListOf(
            "Событие отражает актуализацию состояния клиентского документа.",
            "Полезная нагрузка пригодна для прямого обновления UI без дополнительного чтения.",
        )

        fun snapshot(): StreamEvent {
            return StreamEvent(
                eventId = "evt-${requestId.take(8)}-$sequence",
                eventType = if (heavy) "snapshot-update" else "delta-update",
                documentId = "doc-${requestId.take(8)}-${sequence.toString().padStart(2, '0')}",
                emittedAtEpochMs = generatedAtEpochMs + sequence * DEFAULT_STREAM_INTERVAL_MS,
                revision = 10 + sequence,
                priority = if (heavy || sequence % 3 == 0) "high" else "normal",
                preview = previewForIndex(requestId, sequence, "stream-main"),
                changedFields = changedFields.toList(),
                relatedItems = relatedItems.toList(),
                tags = tags.toList(),
                notes = notes.toList(),
                summary = StreamEventSummary(
                    impactedItems = relatedItems.size + density,
                    warningCount = if (heavy) density else density / 2,
                    scoreDelta = if (heavy) 1.5 + density * 0.1 else 0.5 + density * 0.05,
                    currentStatus = if (heavy) "expanded" else "incremental",
                ),
            )
        }

        var event = snapshot()
        var encodedSize = payloadJson.encodeToString(event).toByteArray(StandardCharsets.UTF_8).size
        var index = 0
        while (encodedSize < targetSizeBytes) {
            when (index % 4) {
                0 -> changedFields += "field_${changedFields.size + 1}"
                1 -> relatedItems += previewForIndex(requestId, sequence + index + 1, "stream-rel")
                2 -> tags += "detail-${tags.size + 1}"
                else -> notes += "Дополнительная заметка ${notes.size + 1} для проверки обработки более плотного события."
            }
            event = snapshot()
            encodedSize = payloadJson.encodeToString(event).toByteArray(StandardCharsets.UTF_8).size
            index += 1
        }
        return event
    }

    private fun previewForIndex(
        requestId: String,
        index: Int,
        suffix: String,
    ): LargeReadPreview {
        val status = when (index % 4) {
            0 -> "active"
            1 -> "limited"
            2 -> "stale"
            else -> "pending"
        }
        return LargeReadPreview(
            documentId = "doc-${requestId.take(8)}-$suffix-${index + 1}",
            title = "Карточка ${index + 1} для $suffix",
            status = status,
            primaryBadge = if (index % 3 == 0) "priority-high" else "priority-normal",
            summaryScore = 74.0 + (index % 9) * 1.75,
        )
    }

    private fun streamAndSessionScenarios(): Set<ScenarioType> {
        return setOf(
            ScenarioType.S7_EVENT_STREAM,
            ScenarioType.S8_HEAVY_EVENT_STREAM,
            ScenarioType.S9_LONG_SESSION,
        )
    }

    private fun buildNarrative(paragraphs: Int): String {
        return List(paragraphs) { index -> buildNarrativeSentence(index) }
            .joinToString(separator = " ")
    }

    private fun buildNarrativeSentence(index: Int): String {
        return when (index % 6) {
            0 -> "Документ агрегирует карточку клиента, коммерческие параметры, ограничения маршрутизации и историю обновлений в одном ответе."
            1 -> "Каждый блок пригоден для прямой отрисовки на экране, но также содержит дополнительные поля для аналитики и проверки консистентности."
            2 -> "Часть параметров поступает из внешних систем, часть рассчитывается на сервере в момент подготовки ответа."
            3 -> "Структура намеренно включает вложенные списки, чтобы нагрузить сериализацию, разбор и клиентское хранение данных."
            4 -> "В состав ответа входят связанные сущности, вложения и служебные признаки, которые используются разными экранами приложения."
            else -> "Для частичного сценария из этого же документа выбирается только компактная проекция, достаточная для первичного отображения в интерфейсе."
        }
    }

    private fun buildNote(index: Int): String {
        return when (index % 5) {
            0 -> "Примечание ${index + 1}: блок проверен серверной валидацией и допускает кэширование."
            1 -> "Примечание ${index + 1}: значения коммерческих параметров синхронизированы с расчетным профилем нагрузки."
            2 -> "Примечание ${index + 1}: часть атрибутов служит для фильтрации и поиска, а не для прямого показа на экране."
            3 -> "Примечание ${index + 1}: история изменений содержит укрупненные события без полного журнала аудита."
            else -> "Примечание ${index + 1}: компактная UI-проекция строится на основе тех же исходных данных без отдельного запроса к источнику."
        }
    }

    private companion object {
        const val DEFAULT_STREAM_INTERVAL_MS = 100L
    }
}
