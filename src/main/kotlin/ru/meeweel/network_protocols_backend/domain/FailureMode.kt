package ru.meeweel.network_protocols_backend.domain

import kotlinx.serialization.Serializable

/**
 * Искусственный режим ошибки для проверки обработки сбоев.
 *
 * В обычных измерениях стоит NONE. Остальные варианты нужны, чтобы убедиться:
 * если backend вернет валидационную ошибку, таймаут или конфликт, клиент не
 * сломается молча и запишет понятную причину.
 */
@Serializable
enum class FailureMode {
    NONE,
    VALIDATION,
    TIMEOUT,
    UNAVAILABLE,
    BUSINESS_CONFLICT,
    INTERNAL,
}
