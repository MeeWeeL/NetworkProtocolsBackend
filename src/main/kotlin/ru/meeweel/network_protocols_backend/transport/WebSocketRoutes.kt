package ru.meeweel.network_protocols_backend.transport

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import ru.meeweel.network_protocols_backend.domain.ErrorResponse
import ru.meeweel.network_protocols_backend.domain.ScenarioRequest
import ru.meeweel.network_protocols_backend.domain.ScenarioType
import ru.meeweel.network_protocols_backend.domain.TransportType
import ru.meeweel.network_protocols_backend.protocol.SocketCommandType
import ru.meeweel.network_protocols_backend.protocol.SocketServerEvent
import ru.meeweel.network_protocols_backend.receiveEnvelope
import ru.meeweel.network_protocols_backend.sendServerEvent
import ru.meeweel.network_protocols_backend.service.BackendException
import ru.meeweel.network_protocols_backend.service.ExperimentService

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriodMillis = 30_000
        timeoutMillis = 30_000
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}

fun Application.configureWebSocketRoutes(
    experimentService: ExperimentService,
    json: Json,
) {
    routing {
        webSocket("/api/ws") {
            sendServerEvent(
                json = json,
                event = SocketServerEvent(
                    name = "session-opened",
                    message = "WebSocket session is ready",
                ),
            )

            while (true) {
                val envelope = receiveEnvelope(json) ?: break
                val command = envelope.command ?: continue
                when (command.command) {
                    SocketCommandType.START_SCENARIO -> {
                        val scenario = command.scenario ?: command.request?.scenario ?: ScenarioType.S7_EVENT_STREAM
                        val request = (command.request ?: ScenarioRequest()).copy(scenario = scenario)
                        try {
                            when (scenario) {
                                ScenarioType.S7_EVENT_STREAM,
                                ScenarioType.S8_HEAVY_EVENT_STREAM,
                                -> {
                                    experimentService.buildStream(scenario, request).collect { response ->
                                        sendServerEvent(
                                            json = json,
                                            event = SocketServerEvent(
                                                name = "stream-event",
                                                scenario = scenario,
                                                response = response,
                                            ),
                                        )
                                    }
                                    sendServerEvent(
                                        json = json,
                                        event = SocketServerEvent(
                                            name = "stream-complete",
                                            scenario = scenario,
                                            message = "Event stream completed",
                                        ),
                                    )
                                }

                                ScenarioType.S9_LONG_SESSION -> {
                                    val response = experimentService.executeWebSocket(scenario, request)
                                    sendServerEvent(
                                        json = json,
                                        event = SocketServerEvent(
                                            name = "session-heartbeat",
                                            scenario = scenario,
                                            response = response,
                                        ),
                                    )
                                }

                                else -> {
                                    val response = experimentService.executeWebSocket(scenario, request)
                                    sendServerEvent(
                                        json = json,
                                        event = SocketServerEvent(
                                            name = "single-response",
                                            scenario = scenario,
                                            response = response,
                                        ),
                                    )
                                }
                            }
                        } catch (error: BackendException) {
                            sendServerEvent(
                                json = json,
                                event = SocketServerEvent(
                                    name = "error",
                                    scenario = scenario,
                                    error = ErrorResponse(
                                        correlationId = request.correlationId ?: request.requestId ?: "unknown",
                                        code = error.code,
                                        message = error.message,
                                        transport = TransportType.WEBSOCKET,
                                        scenario = scenario,
                                    ),
                                ),
                            )
                        }
                    }

                    SocketCommandType.HEARTBEAT -> {
                        try {
                            val response = experimentService.executeWebSocket(
                                scenario = ScenarioType.S9_LONG_SESSION,
                                request = command.request ?: ScenarioRequest(scenario = ScenarioType.S9_LONG_SESSION),
                            )
                            sendServerEvent(
                                json = json,
                                event = SocketServerEvent(
                                    name = "heartbeat-ack",
                                    scenario = ScenarioType.S9_LONG_SESSION,
                                    response = response,
                                ),
                            )
                        } catch (error: BackendException) {
                            sendServerEvent(
                                json = json,
                                event = SocketServerEvent(
                                    name = "error",
                                    scenario = ScenarioType.S9_LONG_SESSION,
                                    error = ErrorResponse(
                                        correlationId = command.request?.correlationId ?: command.request?.requestId ?: "unknown",
                                        code = error.code,
                                        message = error.message,
                                        transport = TransportType.WEBSOCKET,
                                        scenario = ScenarioType.S9_LONG_SESSION,
                                    ),
                                ),
                            )
                        }
                    }

                    SocketCommandType.CLOSE_SESSION -> {
                        sendServerEvent(
                            json = json,
                            event = SocketServerEvent(
                                name = "session-closing",
                                message = "Client requested WebSocket shutdown",
                            ),
                        )
                        break
                    }
                }
            }
        }
    }
}
