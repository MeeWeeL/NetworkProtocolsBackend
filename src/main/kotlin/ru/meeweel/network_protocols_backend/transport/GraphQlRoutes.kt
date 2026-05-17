package ru.meeweel.network_protocols_backend.transport

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import ru.meeweel.network_protocols_backend.service.BackendException
import ru.meeweel.network_protocols_backend.service.ExperimentService

fun Application.configureGraphQlRoutes(
    experimentService: ExperimentService,
    json: Json,
) {
    routing {
        route("/api/graphql") {
            post {
                val request = call.receive<GraphQlRequest>()
                val fallbackCorrelationId = request.variables["correlationId"]?.asStringOrNull()
                    ?: request.variables["requestId"]?.asStringOrNull()
                    ?: UUID.randomUUID().toString()

                val parsed = runCatching { parseGraphQlRequest(request) }
                    .getOrElse { error ->
                        val backendError = error as? BackendException
                        val code = backendError?.code?.name ?: "VALIDATION"
                        call.respond(
                            HttpStatusCode.BadRequest,
                            GraphQlResponse(
                                data = null,
                                errors = listOf(
                                    GraphQlError(
                                        message = error.message ?: "invalid graphql request",
                                        code = code,
                                    ),
                                ),
                                extensions = GraphQlExtensions(
                                    adapter = "GRAPHQL",
                                    correlationId = fallbackCorrelationId,
                                    code = code,
                                ),
                            ),
                        )
                        return@post
                    }

                val correlationId = parsed.request.correlationId
                    ?: parsed.request.requestId
                    ?: fallbackCorrelationId

                try {
                    when (parsed.operation) {
                        GraphQlOperation.QUERY -> {
                            val response = experimentService.executeGraphQl(parsed.scenario, parsed.request)
                            call.respond(
                                GraphQlResponse(
                                    data = GraphQlData(scenario = response),
                                    extensions = GraphQlExtensions(
                                        adapter = "GRAPHQL",
                                        correlationId = correlationId,
                                        scenario = scenarioCode(parsed.scenario),
                                    ),
                                ),
                            )
                        }

                        GraphQlOperation.MUTATION -> {
                            val response = experimentService.executeGraphQl(parsed.scenario, parsed.request)
                            call.respond(
                                GraphQlResponse(
                                    data = GraphQlData(executeScenario = response),
                                    extensions = GraphQlExtensions(
                                        adapter = "GRAPHQL",
                                        correlationId = correlationId,
                                        scenario = scenarioCode(parsed.scenario),
                                    ),
                                ),
                            )
                        }

                        GraphQlOperation.SUBSCRIPTION -> {
                            val stream = experimentService.buildGraphQlStream(parsed.scenario, parsed.request).toList()
                            call.respond(
                                GraphQlResponse(
                                    data = GraphQlData(subscribeScenario = stream),
                                    extensions = GraphQlExtensions(
                                        adapter = "GRAPHQL",
                                        correlationId = correlationId,
                                        scenario = scenarioCode(parsed.scenario),
                                    ),
                                ),
                            )
                        }
                    }
                } catch (error: BackendException) {
                    call.respond(
                        GraphQlResponse(
                            data = null,
                            errors = listOf(
                                GraphQlError(
                                    message = error.message ?: "graphql execution failed",
                                    code = error.code.name,
                                ),
                            ),
                            extensions = GraphQlExtensions(
                                adapter = "GRAPHQL",
                                correlationId = correlationId,
                                code = error.code.name,
                                scenario = scenarioCode(parsed.scenario),
                            ),
                        ),
                    )
                }
            }
        }

        webSocket("/api/graphql/ws") {
            while (true) {
                val initialFrame = incoming.receiveCatching().getOrNull() as? Frame.Text ?: break

                val decodedRequest = runCatching {
                    json.decodeFromString(GraphQlRequest.serializer(), initialFrame.readText())
                }
                val request = decodedRequest.getOrElse { error ->
                    send(
                        Frame.Text(
                            json.encodeToString(
                                buildGraphQlSocketErrorResponse(
                                    error.message ?: "invalid graphql websocket request",
                                ),
                            ),
                        ),
                    )
                    return@getOrElse GraphQlRequest(query = "")
                }
                if (request.query.isBlank()) continue

                val fallbackCorrelationId = request.variables["correlationId"]?.asStringOrNull()
                    ?: request.variables["requestId"]?.asStringOrNull()
                    ?: UUID.randomUUID().toString()

                val parsed = runCatching { parseGraphQlRequest(request) }
                    .getOrElse { error ->
                        val backendError = error as? BackendException
                        val code = backendError?.code?.name ?: "VALIDATION"
                        send(
                            Frame.Text(
                                json.encodeToString(
                                    buildGraphQlSocketErrorResponse(
                                        message = error.message ?: "invalid graphql request",
                                        correlationId = fallbackCorrelationId,
                                        code = code,
                                    ),
                                ),
                            ),
                        )
                        null
                    } ?: continue

                if (parsed.operation != GraphQlOperation.SUBSCRIPTION) {
                    send(
                        Frame.Text(
                            json.encodeToString(
                                buildGraphQlSocketErrorResponse(
                                    message = "GraphQL WebSocket endpoint supports subscription only",
                                    correlationId = parsed.request.correlationId ?: parsed.request.requestId ?: fallbackCorrelationId,
                                    code = "VALIDATION",
                                    scenario = scenarioCode(parsed.scenario),
                                ),
                            ),
                        ),
                    )
                    continue
                }

                val correlationId = parsed.request.correlationId
                    ?: parsed.request.requestId
                    ?: fallbackCorrelationId

                try {
                    experimentService.buildGraphQlStream(parsed.scenario, parsed.request).collect { response ->
                        send(
                            Frame.Text(
                                json.encodeToString(
                                    GraphQlResponse(
                                        data = GraphQlData(subscribeScenario = listOf(response)),
                                        extensions = GraphQlExtensions(
                                            adapter = "GRAPHQL",
                                            correlationId = correlationId,
                                            scenario = scenarioCode(parsed.scenario),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }
                } catch (error: BackendException) {
                    send(
                        Frame.Text(
                            json.encodeToString(
                                buildGraphQlSocketErrorResponse(
                                    message = error.message ?: "graphql execution failed",
                                    correlationId = correlationId,
                                    code = error.code.name,
                                    scenario = scenarioCode(parsed.scenario),
                                ),
                            ),
                        ),
                    )
                }
            }
            close(CloseReason(CloseReason.Codes.NORMAL, "GraphQL session completed"))
        }
    }
}

private fun parseGraphQlRequest(request: GraphQlRequest): ParsedGraphQlRequest {
    val query = request.query.trim()
    val operation = when {
        query.startsWith("mutation") -> GraphQlOperation.MUTATION
        query.startsWith("subscription") -> GraphQlOperation.SUBSCRIPTION
        else -> GraphQlOperation.QUERY
    }

    val fieldName = when (operation) {
        GraphQlOperation.QUERY -> "scenario"
        GraphQlOperation.MUTATION -> "executeScenario"
        GraphQlOperation.SUBSCRIPTION -> "subscribeScenario"
    }

    val args = parseArguments(query, fieldName) + request.variables
    val scenario = scenarioFromExternal(args["scenario"]?.asString() ?: "S1")
    val requestId = args["requestId"]?.asString()
    val correlationId = args["correlationId"]?.asString()
    val sessionId = args["sessionId"]?.asString()
    val payloadSizeBytes = args["payloadSizeBytes"]?.asInt()
    val eventCount = args["eventCount"]?.asInt() ?: 1
    val artificialDelayMs = args["artificialDelayMs"]?.asLong() ?: 0L
    val qClass = args["qClass"]?.asString()
    val loadProfile = args["loadProfile"]?.asString()
    val failureMode = failureModeFromExternal(args["failureMode"]?.asString())
    val payload = args["payload"]?.asString()
    val metadata = args["metadata"]?.asStringMap() ?: emptyMap()

    return ParsedGraphQlRequest(
        operation = operation,
        scenario = scenario,
        request = buildScenarioRequest(
            scenario = scenario,
            requestId = requestId,
            correlationId = correlationId,
            sessionId = sessionId,
            payloadSizeBytes = payloadSizeBytes,
            eventCount = eventCount,
            artificialDelayMs = artificialDelayMs,
            qClass = qClass,
            loadProfile = loadProfile,
            failureMode = failureMode,
            payload = payload,
            metadata = metadata + mapOf("adapter" to "GRAPHQL"),
        ),
    )
}

private fun buildGraphQlSocketErrorResponse(
    message: String,
    correlationId: String = UUID.randomUUID().toString(),
    code: String = "VALIDATION",
    scenario: String? = null,
): GraphQlResponse {
    return GraphQlResponse(
        data = null,
        errors = listOf(GraphQlError(message = message, code = code)),
        extensions = GraphQlExtensions(
            adapter = "GRAPHQL",
            correlationId = correlationId,
            code = code,
            scenario = scenario,
        ),
    )
}

private fun parseArguments(query: String, fieldName: String): Map<String, JsonElement> {
    val fieldPattern = Regex("$fieldName\\s*\\(([^)]*)\\)")
    val match = fieldPattern.find(query) ?: return emptyMap()
    val body = match.groupValues[1].trim()
    if (body.isBlank()) return emptyMap()

    val args = linkedMapOf<String, JsonElement>()
    val argPattern = Regex("""(\w+)\s*:\s*("[^"]*"|\d+)""")
    argPattern.findAll(body).forEach { result ->
        val key = result.groupValues[1]
        val rawValue = result.groupValues[2]
        val value = if (rawValue.startsWith("\"")) {
            JsonPrimitive(rawValue.removeSurrounding("\""))
        } else {
            JsonPrimitive(rawValue.toLong())
        }
        args[key] = value
    }
    return args
}

private fun JsonElement.asString(): String = jsonPrimitive.content

private fun JsonElement.asInt(): Int = jsonPrimitive.content.toInt()

private fun JsonElement.asLong(): Long = jsonPrimitive.content.toLong()

private fun JsonElement.asStringOrNull(): String? = jsonPrimitive.contentOrNull

private fun JsonElement.asStringMap(): Map<String, String> {
    val obj = this as? JsonObject ?: error("metadata must be a JSON object")
    return obj.mapValues { (_, value) ->
        value.jsonPrimitive.contentOrNull
            ?: error("metadata values must be scalar strings")
    }
}
