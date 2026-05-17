package ru.meeweel.network_protocols_backend

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import ru.meeweel.network_protocols_backend.domain.ErrorCode
import ru.meeweel.network_protocols_backend.domain.ErrorResponse
import ru.meeweel.network_protocols_backend.domain.ServiceStatusResponse
import ru.meeweel.network_protocols_backend.grpc.configureGrpcServer
import ru.meeweel.network_protocols_backend.protocol.SocketEnvelope
import ru.meeweel.network_protocols_backend.protocol.SocketEnvelopeType
import ru.meeweel.network_protocols_backend.protocol.SocketServerEvent
import ru.meeweel.network_protocols_backend.service.BackendException
import ru.meeweel.network_protocols_backend.service.ExperimentService
import ru.meeweel.network_protocols_backend.service.toHttpStatus
import ru.meeweel.network_protocols_backend.transport.configureGraphQlRoutes
import ru.meeweel.network_protocols_backend.transport.configureHttpRoutes
import ru.meeweel.network_protocols_backend.transport.configureSoapRoutes
import ru.meeweel.network_protocols_backend.transport.configureWebSocketRoutes
import ru.meeweel.network_protocols_backend.transport.configureWebSockets

fun Application.module() {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    val experimentService = ExperimentService()

    install(CallLogging) {
        level = Level.INFO
    }
    install(ContentNegotiation) {
        json(json)
    }
    install(StatusPages) {
        exception<CancellationException> { _, _ ->
        }
        exception<BackendException> { call, cause ->
            call.respond(
                cause.code.toHttpStatus(),
                ErrorResponse(
                    correlationId = call.request.header("X-Correlation-Id") ?: "unknown",
                    code = cause.code,
                    message = cause.message,
                ),
            )
        }
        exception<Throwable> { call, cause ->
            this@module.environment.log.error("Unhandled backend error", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ServiceStatusResponse(
                    status = "error",
                    message = cause.message ?: "internal server error",
                ),
            )
        }
    }

    configureWebSockets()
    configureHttpRoutes(experimentService)
    configureSoapRoutes(experimentService)
    configureGraphQlRoutes(experimentService, json)
    configureWebSocketRoutes(experimentService, json)
    configureGrpcServer(experimentService)

    routing {
        get("/health") {
            call.respond(
                ServiceStatusResponse(
                    status = "ok",
                    service = "NetworkProtocolsBackend",
                    transport = listOf("rest", "websocket", "soap", "graphql", "grpc"),
                ),
            )
        }
    }
}

suspend fun WebSocketSession.receiveEnvelope(json: Json): SocketEnvelope? {
    val frame = incoming.receiveCatching().getOrNull() ?: return null
    return when (frame) {
        is Frame.Text -> json.decodeFromString(SocketEnvelope.serializer(), frame.readText())
        is Frame.Close -> null
        else -> null
    }
}

suspend fun WebSocketSession.sendServerEvent(json: Json, event: SocketServerEvent) {
    val payload = SocketEnvelope(
        type = SocketEnvelopeType.SERVER_EVENT,
        event = event,
    )
    send(json.encodeToString(SocketEnvelope.serializer(), payload))
}
