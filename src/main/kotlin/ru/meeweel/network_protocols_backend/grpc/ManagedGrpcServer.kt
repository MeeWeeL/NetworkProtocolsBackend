package ru.meeweel.network_protocols_backend.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import ru.meeweel.network_protocols_backend.service.ExperimentService

internal class ManagedGrpcServer(
    private val port: Int,
    experimentService: ExperimentService,
) {
    private val server: Server = ServerBuilder
        .forPort(port)
        .addService(ExperimentGrpcServiceImpl(experimentService))
        .build()
    private var started = false

    fun start() {
        if (!started) {
            server.start()
            started = true
        }
    }

    fun stop() {
        if (started) {
            server.shutdownNow()
            started = false
        }
    }
}
