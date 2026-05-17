package ru.meeweel.network_protocols_backend.grpc

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import ru.meeweel.network_protocols_backend.domain.ScenarioType
import ru.meeweel.network_protocols_backend.service.BackendException
import ru.meeweel.network_protocols_backend.service.ExperimentService
import ru.meeweel.network_protocols_backend.service.toGrpcStatus

class ExperimentGrpcServiceImpl(
    private val experimentService: ExperimentService,
) : ExperimentGrpcServiceGrpcKt.ExperimentGrpcServiceCoroutineImplBase() {

    override suspend fun health(request: HealthRequest): HealthResponse {
        return healthResponse {
            status = "ok"
            service = "NetworkProtocolsBackend"
            transport += listOf("rest", "soap", "graphql", "websocket", "grpc")
        }
    }

    override suspend fun executeScenario(request: GrpcScenarioRequest): GrpcScenarioResponse {
        return grpcMapped {
            val scenario = request.scenario.toDomainScenario()
            val response = experimentService.executeGrpc(
                scenario = scenario,
                request = request.toDomainRequest(),
            )
            response.toGrpcResponse()
        }
    }

    override fun streamScenario(request: GrpcScenarioRequest): Flow<GrpcScenarioResponse> {
        return flow {
            grpcMapped {
                val scenario = request.scenario.toDomainScenario()
                val domainRequest = request.toDomainRequest()
                val responses = when (scenario) {
                    ScenarioType.S7_EVENT_STREAM,
                    ScenarioType.S8_HEAVY_EVENT_STREAM,
                    -> experimentService.buildGrpcStream(scenario, domainRequest)

                    else -> flow {
                        emit(experimentService.executeGrpc(scenario, domainRequest))
                    }
                }
                responses.collect { emit(it.toGrpcResponse()) }
            }
        }
    }

    private suspend fun <T> grpcMapped(block: suspend () -> T): T {
        return try {
            block()
        } catch (error: BackendException) {
            throw error.code.toGrpcStatus()
                .withDescription(error.message)
                .withCause(error)
                .asRuntimeException()
        }
    }
}
