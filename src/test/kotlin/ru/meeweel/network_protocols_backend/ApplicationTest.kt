package ru.meeweel.network_protocols_backend

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.meeweel.network_protocols_backend.domain.ErrorCode
import ru.meeweel.network_protocols_backend.domain.ErrorResponse
import ru.meeweel.network_protocols_backend.domain.ScenarioRequest
import ru.meeweel.network_protocols_backend.domain.ScenarioResponse
import ru.meeweel.network_protocols_backend.domain.ScenarioType
import ru.meeweel.network_protocols_backend.protocol.SocketClientCommand
import ru.meeweel.network_protocols_backend.protocol.SocketCommandType
import ru.meeweel.network_protocols_backend.protocol.SocketEnvelope
import ru.meeweel.network_protocols_backend.protocol.SocketEnvelopeType

class ApplicationTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun healthEndpointRespondsOk() = testApplication {
        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("NetworkProtocolsBackend"))
    }

    @Test
    fun restScenarioEndpointReturnsPayload() = testApplication {
        val response = client.post("/api/rest/scenarios/S1") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ScenarioRequest.serializer(),
                    ScenarioRequest(payloadSizeBytes = 128),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decoded = json.decodeFromString(ScenarioResponse.serializer(), response.bodyAsText())
        assertEquals(ScenarioType.S1_SHORT_READ, decoded.scenario)
        assertEquals(128, decoded.payloadSizeBytes)
    }

    @Test
    fun restLargeReadScenarioReturnsStructuredDocument() = testApplication {
        val response = client.post("/api/rest/scenarios/S2") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ScenarioRequest.serializer(),
                    ScenarioRequest(payloadSizeBytes = 8_192),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decoded = json.decodeFromString(ScenarioResponse.serializer(), response.bodyAsText())
        assertEquals(ScenarioType.S2_LARGE_READ, decoded.scenario)
        assertNull(decoded.payload)
        val document = assertNotNull(decoded.document)
        assertTrue(document.lineItems.isNotEmpty())
        assertTrue(document.parameterGroups.isNotEmpty())
    }

    @Test
    fun restPartialLargeReadScenarioReturnsCompactPreview() = testApplication {
        val response = client.post("/api/rest/scenarios/S3") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ScenarioRequest.serializer(),
                    ScenarioRequest(payloadSizeBytes = 768),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decoded = json.decodeFromString(ScenarioResponse.serializer(), response.bodyAsText())
        assertEquals(ScenarioType.S3_PARTIAL_LARGE_READ, decoded.scenario)
        assertNull(decoded.payload)
        assertNull(decoded.document)
        val preview = assertNotNull(decoded.preview)
        assertTrue(preview.title.isNotBlank())
    }

    @Test
    fun restPageReadScenarioReturnsStructuredPage() = testApplication {
        val response = client.post("/api/rest/scenarios/S4") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    ScenarioRequest.serializer(),
                    ScenarioRequest(payloadSizeBytes = 6_144),
                ),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val decoded = json.decodeFromString(ScenarioResponse.serializer(), response.bodyAsText())
        assertEquals(ScenarioType.S4_PAGE_READ, decoded.scenario)
        assertNull(decoded.payload)
        assertNull(decoded.document)
        assertNotNull(decoded.page)
        assertTrue(decoded.page.items.isNotEmpty())
        assertTrue(decoded.page.facets.isNotEmpty())
    }

    @Test
    fun invalidRestScenarioReturnsValidationError() = testApplication {
        val response = client.get("/api/rest/scenarios/BAD")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val decoded = json.decodeFromString(ErrorResponse.serializer(), response.bodyAsText())
        assertEquals(ErrorCode.VALIDATION, decoded.code)
    }

    @Test
    fun websocketStreamScenarioProducesEvents() = testApplication {
        val wsClient = createClient {
            install(WebSockets)
        }

        wsClient.webSocket("/api/ws") {
            incoming.receive()

            val payload = SocketEnvelope(
                type = SocketEnvelopeType.CLIENT_COMMAND,
                command = SocketClientCommand(
                    command = SocketCommandType.START_SCENARIO,
                    scenario = ScenarioType.S7_EVENT_STREAM,
                    request = ScenarioRequest(
                        eventCount = 2,
                    ),
                ),
            )
            send(Frame.Text(json.encodeToString(SocketEnvelope.serializer(), payload)))

            val firstEvent = (incoming.receive() as Frame.Text).readText()
            val secondEvent = (incoming.receive() as Frame.Text).readText()
            val completeEvent = (incoming.receive() as Frame.Text).readText()

            assertTrue(firstEvent.contains("stream-event"))
            assertTrue(secondEvent.contains("stream-event"))
            assertTrue(completeEvent.contains("stream-complete"))
        }
    }
}
