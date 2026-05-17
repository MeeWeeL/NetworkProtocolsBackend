package ru.meeweel.network_protocols_backend

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import ru.meeweel.network_protocols_backend.domain.ErrorCode
import ru.meeweel.network_protocols_backend.domain.ScenarioType
import ru.meeweel.network_protocols_backend.transport.GraphQlRequest
import ru.meeweel.network_protocols_backend.transport.GraphQlResponse

class GraphQlRoutesTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun graphQlQueryReturnsScenarioData() = testApplication {
        val response = client.post("/api/graphql") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    GraphQlRequest.serializer(),
                    GraphQlRequest(
                        query = """
                            query {
                              scenario(scenario: "S1", payloadSizeBytes: 128) {
                                requestId
                                scenario
                                payloadSizeBytes
                              }
                            }
                        """.trimIndent(),
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decoded = json.decodeFromString(GraphQlResponse.serializer(), response.bodyAsText())
        assertEquals("GRAPHQL", decoded.extensions.adapter)
        val scenario = assertNotNull(decoded.data?.scenario)
        assertEquals(ScenarioType.S1_SHORT_READ, scenario.scenario)
        assertEquals(128, scenario.payloadSizeBytes)
    }

    @Test
    fun graphQlMutationReturnsAckData() = testApplication {
        val response = client.post("/api/graphql") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    GraphQlRequest.serializer(),
                    GraphQlRequest(
                        query = """
                            mutation {
                              executeScenario(scenario: "S3", payload: "hello") {
                                requestId
                                status
                                payloadChecksum
                              }
                            }
                        """.trimIndent(),
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decoded = json.decodeFromString(GraphQlResponse.serializer(), response.bodyAsText())
        val scenario = assertNotNull(decoded.data?.executeScenario)
        assertEquals("ok", scenario.status)
        assertNotNull(scenario.payloadChecksum)
    }

    @Test
    fun graphQlSubscriptionReturnsStreamBatch() = testApplication {
        val response = client.post("/api/graphql") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    GraphQlRequest.serializer(),
                    GraphQlRequest(
                        query = """
                            subscription {
                              subscribeScenario(scenario: "S4", eventCount: 3, payloadSizeBytes: 96) {
                                requestId
                                sequence
                              }
                            }
                        """.trimIndent(),
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decoded = json.decodeFromString(GraphQlResponse.serializer(), response.bodyAsText())
        val stream = assertNotNull(decoded.data?.subscribeScenario)
        assertEquals(3, stream.size)
        assertEquals(1, stream.first().sequence)
        assertEquals(3, stream.last().sequence)
    }

    @Test
    fun graphQlWebSocketSubscriptionStreamsEvents() = testApplication {
        val wsClient = createClient {
            install(WebSockets)
        }

        wsClient.webSocket("/api/graphql/ws") {
            send(
                Frame.Text(
                    json.encodeToString(
                        GraphQlRequest.serializer(),
                        GraphQlRequest(
                            query = """
                                subscription {
                                  subscribeScenario(scenario: "S4", eventCount: 3, payloadSizeBytes: 96) {
                                    requestId
                                    sequence
                                  }
                                }
                            """.trimIndent(),
                        ),
                    ),
                ),
            )

            val first = json.decodeFromString(
                GraphQlResponse.serializer(),
                (incoming.receive() as Frame.Text).readText(),
            )
            val second = json.decodeFromString(
                GraphQlResponse.serializer(),
                (incoming.receive() as Frame.Text).readText(),
            )
            val third = json.decodeFromString(
                GraphQlResponse.serializer(),
                (incoming.receive() as Frame.Text).readText(),
            )

            assertEquals(1, assertNotNull(first.data?.subscribeScenario).single().sequence)
            assertEquals(2, assertNotNull(second.data?.subscribeScenario).single().sequence)
            assertEquals(3, assertNotNull(third.data?.subscribeScenario).single().sequence)
        }
    }

    @Test
    fun graphQlWebSocketSupportsSequentialSubscriptionsInSingleSession() = testApplication {
        val wsClient = createClient {
            install(WebSockets)
        }

        fun buildSubscriptionRequest(
            requestId: String,
            correlationId: String,
        ): String {
            return json.encodeToString(
                GraphQlRequest.serializer(),
                GraphQlRequest(
                    query = """
                        subscription {
                          subscribeScenario(
                            scenario: ${'$'}scenario,
                            requestId: ${'$'}requestId,
                            correlationId: ${'$'}correlationId,
                            eventCount: ${'$'}eventCount,
                            payloadSizeBytes: ${'$'}payloadSizeBytes,
                            metadata: ${'$'}metadata
                          ) {
                            requestId
                            sequence
                            metadata
                          }
                        }
                    """.trimIndent(),
                    variables = buildJsonObject {
                        put("scenario", "S4")
                        put("requestId", requestId)
                        put("correlationId", correlationId)
                        put("eventCount", 2)
                        put("payloadSizeBytes", 96)
                        put("metadata", buildJsonObject {
                            put("client", "android-benchmark")
                        })
                    },
                ),
            )
        }

        wsClient.webSocket("/api/graphql/ws") {
            send(Frame.Text(buildSubscriptionRequest(requestId = "ws-seq-1", correlationId = "ws-corr-1")))

            val firstRun = List(2) {
                json.decodeFromString(
                    GraphQlResponse.serializer(),
                    (incoming.receive() as Frame.Text).readText(),
                )
            }

            assertEquals(1, assertNotNull(firstRun[0].data?.subscribeScenario).single().sequence)
            assertEquals(2, assertNotNull(firstRun[1].data?.subscribeScenario).single().sequence)
            assertEquals("ws-seq-1", assertNotNull(firstRun[0].data?.subscribeScenario).single().requestId)
            assertEquals(
                "android-benchmark",
                assertNotNull(firstRun[0].data?.subscribeScenario).single().metadata["client"],
            )

            send(Frame.Text(buildSubscriptionRequest(requestId = "ws-seq-2", correlationId = "ws-corr-2")))

            val secondRun = List(2) {
                json.decodeFromString(
                    GraphQlResponse.serializer(),
                    (incoming.receive() as Frame.Text).readText(),
                )
            }

            assertEquals(1, assertNotNull(secondRun[0].data?.subscribeScenario).single().sequence)
            assertEquals(2, assertNotNull(secondRun[1].data?.subscribeScenario).single().sequence)
            assertEquals("ws-seq-2", assertNotNull(secondRun[0].data?.subscribeScenario).single().requestId)
            assertEquals(
                "android-benchmark",
                assertNotNull(secondRun[0].data?.subscribeScenario).single().metadata["client"],
            )
        }
    }

    @Test
    fun graphQlVariablesPropagateIntoCanonicalRequest() = testApplication {
        val response = client.post("/api/graphql") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    GraphQlRequest.serializer(),
                    GraphQlRequest(
                        query = """
                            query {
                              scenario(scenario: "S5", payloadSizeBytes: 128) {
                                requestId
                                sessionId
                                metadata
                              }
                            }
                        """.trimIndent(),
                        variables = buildJsonObject {
                            put("correlationId", "gql-correlation-1")
                            put("sessionId", "gql-session-1")
                            put("qClass", "mixed")
                            put("loadProfile", "L2")
                            put("metadata", buildJsonObject {
                                put("client", "android-benchmark")
                            })
                        },
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decoded = json.decodeFromString(GraphQlResponse.serializer(), response.bodyAsText())
        val scenario = assertNotNull(decoded.data?.scenario)
        assertEquals("gql-correlation-1", scenario.correlationId)
        assertEquals("gql-session-1", scenario.sessionId)
        assertEquals("mixed", scenario.metadata["qClass"])
        assertEquals("L2", scenario.metadata["loadProfile"])
        assertEquals("android-benchmark", scenario.metadata["client"])
    }

    @Test
    fun graphQlDomainFailureReturnsErrorPayload() = testApplication {
        val response = client.post("/api/graphql") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    GraphQlRequest.serializer(),
                    GraphQlRequest(
                        query = """
                            query {
                              scenario(scenario: "S1", payloadSizeBytes: 128) {
                                requestId
                              }
                            }
                        """.trimIndent(),
                        variables = buildJsonObject {
                            put("correlationId", "gql-failure-1")
                            put("failureMode", "TIMEOUT")
                        },
                    ),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decoded = json.decodeFromString(GraphQlResponse.serializer(), response.bodyAsText())
        assertEquals(null, decoded.data)
        assertEquals(ErrorCode.TIMEOUT.name, decoded.errors.single().code)
        assertEquals("gql-failure-1", decoded.extensions.correlationId)
        assertEquals(ErrorCode.TIMEOUT.name, decoded.extensions.code)
    }
}
