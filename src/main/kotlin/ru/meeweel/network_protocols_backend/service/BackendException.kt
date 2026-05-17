package ru.meeweel.network_protocols_backend.service

import ru.meeweel.network_protocols_backend.domain.ErrorCode

class BackendException(
    val code: ErrorCode,
    override val message: String,
) : RuntimeException(message)
