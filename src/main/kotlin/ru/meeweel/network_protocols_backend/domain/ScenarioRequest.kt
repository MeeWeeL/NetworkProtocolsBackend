package ru.meeweel.network_protocols_backend.domain

import kotlinx.serialization.Serializable

/**
 * Входной запрос сценария.
 *
 * Здесь лежит не "REST-запрос" или "gRPC-запрос", а общий смысл операции:
 * какой сценарий выполнить, какого размера данные нужны, сколько событий
 * ждать и какие служебные метки потом попадут в отчет.
 */
@Serializable
data class ScenarioRequest(
    val requestId: String? = null,
    val correlationId: String? = null,
    val sessionId: String? = null,
    val scenario: ScenarioType? = null,
    val payloadSizeBytes: Int? = null,
    val eventCount: Int = 1,
    val artificialDelayMs: Long = 0,
    val qClass: String? = null,
    val loadProfile: String? = null,
    val failureMode: FailureMode = FailureMode.NONE,
    val metadata: Map<String, String> = emptyMap(),
    val payload: String? = null,
)
