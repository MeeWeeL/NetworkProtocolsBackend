package ru.meeweel.network_protocols_backend.transport

import ru.meeweel.network_protocols_backend.domain.ErrorCode
import ru.meeweel.network_protocols_backend.domain.FailureMode
import ru.meeweel.network_protocols_backend.domain.ScenarioRequest
import ru.meeweel.network_protocols_backend.domain.ScenarioType
import ru.meeweel.network_protocols_backend.service.BackendException

internal fun scenarioFromExternal(value: String): ScenarioType {
    // В запросах снаружи сценарий приходит коротким кодом S1-S9.
    // Здесь мы превращаем этот код в enum, чтобы дальше весь backend работал
    // с типобезопасным значением, а не с произвольной строкой.
    return when (value.trim().uppercase()) {
        "S1" -> ScenarioType.S1_SHORT_READ
        "S2" -> ScenarioType.S2_LARGE_READ
        "S3" -> ScenarioType.S3_PARTIAL_LARGE_READ
        "S4" -> ScenarioType.S4_PAGE_READ
        "S5" -> ScenarioType.S5_SMALL_WRITE_ACK
        "S6" -> ScenarioType.S6_LARGE_WRITE_ACK
        "S7" -> ScenarioType.S7_EVENT_STREAM
        "S8" -> ScenarioType.S8_HEAVY_EVENT_STREAM
        "S9" -> ScenarioType.S9_LONG_SESSION
        else -> throw BackendException(
            code = ErrorCode.VALIDATION,
            message = "Unsupported scenario: $value",
        )
    }
}

internal fun failureModeFromExternal(value: String?): FailureMode {
    if (value.isNullOrBlank()) return FailureMode.NONE
    return when (value.trim().uppercase()) {
        "NONE" -> FailureMode.NONE
        "VALIDATION" -> FailureMode.VALIDATION
        "TIMEOUT" -> FailureMode.TIMEOUT
        "UNAVAILABLE" -> FailureMode.UNAVAILABLE
        "BUSINESS_CONFLICT" -> FailureMode.BUSINESS_CONFLICT
        "INTERNAL" -> FailureMode.INTERNAL
        else -> throw BackendException(
            code = ErrorCode.VALIDATION,
            message = "Unsupported failure mode: $value",
        )
    }
}

internal fun scenarioCode(scenario: ScenarioType): String {
    return when (scenario) {
        ScenarioType.S1_SHORT_READ -> "S1"
        ScenarioType.S2_LARGE_READ -> "S2"
        ScenarioType.S3_PARTIAL_LARGE_READ -> "S3"
        ScenarioType.S4_PAGE_READ -> "S4"
        ScenarioType.S5_SMALL_WRITE_ACK -> "S5"
        ScenarioType.S6_LARGE_WRITE_ACK -> "S6"
        ScenarioType.S7_EVENT_STREAM -> "S7"
        ScenarioType.S8_HEAVY_EVENT_STREAM -> "S8"
        ScenarioType.S9_LONG_SESSION -> "S9"
    }
}

internal fun buildScenarioRequest(
    scenario: ScenarioType,
    requestId: String? = null,
    correlationId: String? = null,
    sessionId: String? = null,
    payloadSizeBytes: Int? = null,
    eventCount: Int = 1,
    artificialDelayMs: Long = 0,
    qClass: String? = null,
    loadProfile: String? = null,
    failureMode: FailureMode = FailureMode.NONE,
    payload: String? = null,
    metadata: Map<String, String> = emptyMap(),
): ScenarioRequest {
    // Эта функция собирает единый доменный запрос из REST/SOAP/GraphQL/WebSocket/gRPC.
    // Благодаря этому все транспортные ветки дальше попадают в одну и ту же
    // прикладную логику, а сравнение остается функционально сопоставимым.
    return ScenarioRequest(
        requestId = requestId,
        correlationId = correlationId,
        sessionId = sessionId,
        scenario = scenario,
        payloadSizeBytes = payloadSizeBytes,
        eventCount = eventCount,
        artificialDelayMs = artificialDelayMs,
        qClass = qClass,
        loadProfile = loadProfile,
        failureMode = failureMode,
        payload = payload,
        metadata = metadata,
    )
}
