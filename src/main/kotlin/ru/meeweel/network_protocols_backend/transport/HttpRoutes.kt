package ru.meeweel.network_protocols_backend.transport

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import ru.meeweel.network_protocols_backend.domain.ScenarioRequest
import ru.meeweel.network_protocols_backend.domain.ScenarioType
import ru.meeweel.network_protocols_backend.service.BackendException
import ru.meeweel.network_protocols_backend.service.ExperimentService

fun Application.configureHttpRoutes(
    experimentService: ExperimentService,
) {
    routing {
        route("/api/rest") {
            get("/catalog") {
                call.respond(experimentService.scenarioCatalog())
            }

            get("/scenarios") {
                call.respond(ScenarioType.entries)
            }

            get("/scenarios/{scenario}") {
                val rawScenario = call.parameters["scenario"]
                    ?: throw BackendException(
                        code = ru.meeweel.network_protocols_backend.domain.ErrorCode.VALIDATION,
                        message = "scenario path parameter is required",
                    )
                val scenario = scenarioFromExternal(rawScenario)
                val request = ScenarioRequest(
                    requestId = call.request.queryParameters["requestId"],
                    scenario = scenario,
                    correlationId = call.request.header("X-Correlation-Id"),
                    sessionId = call.request.queryParameters["sessionId"],
                    payloadSizeBytes = call.request.queryParameters["payloadSizeBytes"]?.toIntOrNull(),
                    eventCount = call.request.queryParameters["eventCount"]?.toIntOrNull() ?: 1,
                    artificialDelayMs = call.request.queryParameters["artificialDelayMs"]?.toLongOrNull() ?: 0,
                    qClass = call.request.queryParameters["qClass"],
                    loadProfile = call.request.queryParameters["loadProfile"],
                    failureMode = failureModeFromExternal(call.request.queryParameters["failureMode"]),
                )
                call.respond(experimentService.executeRest(scenario, request))
            }

            post("/scenarios/{scenario}") {
                val rawScenario = call.parameters["scenario"]
                    ?: throw BackendException(
                        code = ru.meeweel.network_protocols_backend.domain.ErrorCode.VALIDATION,
                        message = "scenario path parameter is required",
                    )
                val scenario = scenarioFromExternal(rawScenario)
                val body = call.receive<ScenarioRequest>()
                val request = body.copy(
                    scenario = scenario,
                    correlationId = call.request.header("X-Correlation-Id") ?: body.correlationId,
                )
                call.respond(experimentService.executeRest(scenario, request))
            }
        }
    }
}
