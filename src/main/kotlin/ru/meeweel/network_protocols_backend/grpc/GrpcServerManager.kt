package ru.meeweel.network_protocols_backend.grpc

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.log
import ru.meeweel.network_protocols_backend.service.ExperimentService

private const val DEFAULT_GRPC_PORT = 9090

fun Application.configureGrpcServer(
    experimentService: ExperimentService,
) {
    val port = environment.config.propertyOrNull("grpc.deployment.port")
        ?.getString()
        ?.toIntOrNull()
        ?: DEFAULT_GRPC_PORT
    val grpcServer = ManagedGrpcServer(
        port = port,
        experimentService = experimentService,
    )

    environment.monitor.subscribe(ApplicationStarted) {
        grpcServer.start()
        log.info("gRPC backend started on port {}", port)
    }
    environment.monitor.subscribe(ApplicationStopped) {
        grpcServer.stop()
        log.info("gRPC backend stopped")
    }
}
