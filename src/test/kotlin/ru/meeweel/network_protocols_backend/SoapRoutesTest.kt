package ru.meeweel.network_protocols_backend

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.meeweel.network_protocols_backend.service.ExperimentService
import ru.meeweel.network_protocols_backend.transport.configureSoapRoutes

class SoapRoutesTest {
    @Test
    fun soapWsdlIsAvailable() = testApplication {
        application {
            configureSoapRoutes(ExperimentService())
        }

        val response = client.get("/api/soap/wsdl")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("<definitions"))
    }

    @Test
    fun soapExecuteScenarioReturnsEnvelope() = testApplication {
        application {
            configureSoapRoutes(ExperimentService())
        }

        val response = client.post("/api/soap") {
            setBody(
                """
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:svc="urn:meeweel:network_protocols_backend">
                      <soapenv:Header>
                        <svc:CorrelationId>soap-test-1</svc:CorrelationId>
                      </soapenv:Header>
                      <soapenv:Body>
                        <svc:ExecuteScenarioRequest>
                          <svc:Scenario>S2</svc:Scenario>
                          <svc:RequestId>soap-request-1</svc:RequestId>
                          <svc:PayloadSizeBytes>4096</svc:PayloadSizeBytes>
                          <svc:Metadata>
                            <svc:Entry key="client">android-benchmark</svc:Entry>
                          </svc:Metadata>
                        </svc:ExecuteScenarioRequest>
                      </soapenv:Body>
                    </soapenv:Envelope>
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("ExecuteScenarioResponse"))
        assertTrue(body.contains("<svc:RequestId>soap-request-1</svc:RequestId>"))
        assertTrue(body.contains("<svc:Scenario>S2</svc:Scenario>"))
        assertTrue(body.contains("<svc:CanonicalOperation>readLarge</svc:CanonicalOperation>"))
        assertTrue(body.contains("<svc:Transport>SOAP</svc:Transport>"))
        assertTrue(body.contains("<svc:ServerProcessingTimeMs>"))
        assertTrue(body.contains("<svc:ProtocolAdapter>SOAP</svc:ProtocolAdapter>"))
        assertTrue(body.contains("android-benchmark"))
    }

    @Test
    fun soapInvalidScenarioReturnsFault() = testApplication {
        application {
            configureSoapRoutes(ExperimentService())
        }

        val response = client.post("/api/soap") {
            setBody(
                """
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:svc="urn:meeweel:network_protocols_backend">
                      <soapenv:Body>
                        <svc:ExecuteScenarioRequest>
                          <svc:Scenario>BAD</svc:Scenario>
                        </svc:ExecuteScenarioRequest>
                      </soapenv:Body>
                    </soapenv:Envelope>
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("<soapenv:Fault>"))
        assertTrue(body.contains("faultcode"))
    }

    @Test
    fun soapBackendFailureReturnsMappedFault() = testApplication {
        application {
            configureSoapRoutes(ExperimentService())
        }

        val response = client.post("/api/soap") {
            setBody(
                """
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:svc="urn:meeweel:network_protocols_backend">
                      <soapenv:Header>
                        <svc:CorrelationId>soap-failure-1</svc:CorrelationId>
                      </soapenv:Header>
                      <soapenv:Body>
                        <svc:ExecuteScenarioRequest>
                          <svc:Scenario>S1</svc:Scenario>
                          <svc:FailureMode>TIMEOUT</svc:FailureMode>
                        </svc:ExecuteScenarioRequest>
                      </soapenv:Body>
                    </soapenv:Envelope>
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.GatewayTimeout, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("<soapenv:Fault>"))
        assertTrue(body.contains("<svc:ErrorCode>TIMEOUT</svc:ErrorCode>"))
        assertTrue(body.contains("<svc:Scenario>S1</svc:Scenario>"))
        assertTrue(body.contains("<svc:CorrelationId>soap-failure-1</svc:CorrelationId>"))
    }
}
