package ru.meeweel.network_protocols_backend

import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.flow.toList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import ru.meeweel.network_protocols_backend.grpc.ExperimentGrpcServiceGrpcKt
import ru.meeweel.network_protocols_backend.grpc.GrpcFailureMode
import ru.meeweel.network_protocols_backend.grpc.ExperimentGrpcServiceImpl
import ru.meeweel.network_protocols_backend.grpc.GrpcScenarioType
import ru.meeweel.network_protocols_backend.grpc.grpcScenarioRequest
import ru.meeweel.network_protocols_backend.grpc.healthRequest
import ru.meeweel.network_protocols_backend.service.ExperimentService

class GrpcServerTest {
    private val serverName = InProcessServerBuilder.generateName()
    private lateinit var server: Server
    private lateinit var channel: ManagedChannel
    private lateinit var stub: ExperimentGrpcServiceGrpcKt.ExperimentGrpcServiceCoroutineStub

    @BeforeTest
    fun setUp() {
        val service = ExperimentGrpcServiceImpl(ExperimentService())
        server = InProcessServerBuilder
            .forName(serverName)
            .directExecutor()
            .addService(service)
            .build()
            .start()

        channel = InProcessChannelBuilder
            .forName(serverName)
            .directExecutor()
            .build()
        stub = ExperimentGrpcServiceGrpcKt.ExperimentGrpcServiceCoroutineStub(channel)
    }

    @AfterTest
    fun tearDown() {
        channel.shutdownNow()
        server.shutdownNow()
    }

    @Test
    fun healthReportsGrpcTransport() = kotlinx.coroutines.runBlocking {
        val response = stub.health(healthRequest {})

        assertEquals("ok", response.status)
        assertTrue(response.transportList.contains("grpc"))
    }

    @Test
    fun unaryScenarioReturnsPayload() = kotlinx.coroutines.runBlocking {
        val response = stub.executeScenario(
            grpcScenarioRequest {
                scenario = GrpcScenarioType.S1_SHORT_READ
                payloadSizeBytes = 128
            },
        )

        assertEquals(GrpcScenarioType.S1_SHORT_READ, response.scenario)
        assertEquals(128, response.payloadSizeBytes)
        assertTrue(response.payload.isNotBlank())
    }

    @Test
    fun streamingScenarioProducesExpectedEventCount() = kotlinx.coroutines.runBlocking {
        val responses = stub.streamScenario(
            grpcScenarioRequest {
                scenario = GrpcScenarioType.S7_EVENT_STREAM
                eventCount = 3
                payloadSizeBytes = 96
            },
        ).toList()

        assertEquals(3, responses.size)
        assertEquals(1, responses.first().sequence)
        assertEquals(3, responses.last().sequence)
        assertTrue(responses.all { it.scenario == GrpcScenarioType.S7_EVENT_STREAM })
    }

    @Test
    fun heavyStreamingScenarioReturnsStructuredEvent() = kotlinx.coroutines.runBlocking {
        val responses = stub.streamScenario(
            grpcScenarioRequest {
                scenario = GrpcScenarioType.S8_HEAVY_EVENT_STREAM
                eventCount = 2
                payloadSizeBytes = 2_048
            },
        ).toList()

        assertEquals(2, responses.size)
        assertTrue(responses.all { it.hasStreamEvent() })
        assertTrue(responses.all { it.streamEvent.relatedItemsCount > 0 })
    }

    @Test
    fun grpcFailureModeMapsToStatusCode() = kotlinx.coroutines.runBlocking {
        val error = assertFailsWith<Exception> {
            stub.executeScenario(
                grpcScenarioRequest {
                    scenario = GrpcScenarioType.S1_SHORT_READ
                    failureMode = GrpcFailureMode.TIMEOUT
                },
            )
        }

        assertEquals(Status.Code.DEADLINE_EXCEEDED, Status.fromThrowable(error).code)
    }
}
