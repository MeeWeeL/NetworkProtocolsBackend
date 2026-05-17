package ru.meeweel.network_protocols_backend.service

import io.grpc.Status
import io.ktor.http.HttpStatusCode
import ru.meeweel.network_protocols_backend.domain.ErrorCode

fun ErrorCode.toHttpStatus(): HttpStatusCode {
    return when (this) {
        ErrorCode.VALIDATION -> HttpStatusCode.BadRequest
        ErrorCode.TIMEOUT -> HttpStatusCode.GatewayTimeout
        ErrorCode.UNAVAILABLE -> HttpStatusCode.ServiceUnavailable
        ErrorCode.BUSINESS_CONFLICT -> HttpStatusCode.Conflict
        ErrorCode.INTERNAL -> HttpStatusCode.InternalServerError
    }
}

fun ErrorCode.toGrpcStatus(): Status {
    return when (this) {
        ErrorCode.VALIDATION -> Status.INVALID_ARGUMENT
        ErrorCode.TIMEOUT -> Status.DEADLINE_EXCEEDED
        ErrorCode.UNAVAILABLE -> Status.UNAVAILABLE
        ErrorCode.BUSINESS_CONFLICT -> Status.ABORTED
        ErrorCode.INTERNAL -> Status.INTERNAL
    }
}
