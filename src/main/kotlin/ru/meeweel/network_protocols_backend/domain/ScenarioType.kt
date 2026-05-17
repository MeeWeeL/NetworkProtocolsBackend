package ru.meeweel.network_protocols_backend.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Серверный список сценариев S1-S9.
 *
 * Это тот же набор "упражнений", что и в Android-клиенте. Backend держит его
 * у себя, чтобы не доверять случайной строке из сети и явно понимать, какую
 * прикладную работу нужно выполнить.
 */
@Serializable
enum class ScenarioType {
    @SerialName("S1")
    S1_SHORT_READ,

    @SerialName("S2")
    S2_LARGE_READ,

    @SerialName("S3")
    S3_PARTIAL_LARGE_READ,

    @SerialName("S4")
    S4_PAGE_READ,

    @SerialName("S5")
    S5_SMALL_WRITE_ACK,

    @SerialName("S6")
    S6_LARGE_WRITE_ACK,

    @SerialName("S7")
    S7_EVENT_STREAM,

    @SerialName("S8")
    S8_HEAVY_EVENT_STREAM,

    @SerialName("S9")
    S9_LONG_SESSION,
}
