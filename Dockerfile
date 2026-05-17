FROM gradle:8.13-jdk17 AS builder

WORKDIR /app

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src ./src

RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/build/install/NetworkProtocolsBackend ./NetworkProtocolsBackend

EXPOSE 8080 9090

ENV PORT=8080
ENV GRPC_PORT=9090

CMD ["./NetworkProtocolsBackend/bin/NetworkProtocolsBackend"]
