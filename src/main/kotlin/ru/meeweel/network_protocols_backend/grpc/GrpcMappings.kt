package ru.meeweel.network_protocols_backend.grpc

import ru.meeweel.network_protocols_backend.domain.ScenarioRequest
import ru.meeweel.network_protocols_backend.domain.ScenarioResponse
import ru.meeweel.network_protocols_backend.domain.ScenarioType
import ru.meeweel.network_protocols_backend.domain.FailureMode
import ru.meeweel.network_protocols_backend.domain.ErrorCode
import ru.meeweel.network_protocols_backend.domain.TransportType
import ru.meeweel.network_protocols_backend.service.BackendException

fun GrpcScenarioRequest.toDomainRequest(): ScenarioRequest {
    return ScenarioRequest(
        requestId = requestId.takeIf { it.isNotBlank() },
        correlationId = correlationId.takeIf { it.isNotBlank() },
        sessionId = sessionId.takeIf { it.isNotBlank() },
        scenario = scenario.toDomainScenario(),
        payloadSizeBytes = payloadSizeBytes.takeIf { it > 0 },
        eventCount = eventCount.takeIf { it > 0 } ?: 1,
        artificialDelayMs = artificialDelayMs,
        qClass = qClass.takeIf { it.isNotBlank() },
        loadProfile = loadProfile.takeIf { it.isNotBlank() },
        failureMode = failureMode.toDomainFailureMode(),
        metadata = metadataMap,
        payload = payload.takeIf { it.isNotBlank() },
    )
}

fun GrpcScenarioType.toDomainScenario(): ScenarioType {
    return when (this) {
        GrpcScenarioType.S1_SHORT_READ -> ScenarioType.S1_SHORT_READ
        GrpcScenarioType.S2_LARGE_READ -> ScenarioType.S2_LARGE_READ
        GrpcScenarioType.S3_PARTIAL_LARGE_READ -> ScenarioType.S3_PARTIAL_LARGE_READ
        GrpcScenarioType.S4_PAGE_READ -> ScenarioType.S4_PAGE_READ
        GrpcScenarioType.S5_SMALL_WRITE_ACK -> ScenarioType.S5_SMALL_WRITE_ACK
        GrpcScenarioType.S6_LARGE_WRITE_ACK -> ScenarioType.S6_LARGE_WRITE_ACK
        GrpcScenarioType.S7_EVENT_STREAM -> ScenarioType.S7_EVENT_STREAM
        GrpcScenarioType.S8_HEAVY_EVENT_STREAM -> ScenarioType.S8_HEAVY_EVENT_STREAM
        GrpcScenarioType.S9_LONG_SESSION -> ScenarioType.S9_LONG_SESSION
        GrpcScenarioType.UNRECOGNIZED,
        GrpcScenarioType.GRPC_SCENARIO_UNSPECIFIED,
        -> throw BackendException(
            code = ErrorCode.VALIDATION,
            message = "Unsupported gRPC scenario: $this",
        )
    }
}

fun GrpcFailureMode.toDomainFailureMode(): FailureMode {
    return when (this) {
        GrpcFailureMode.GRPC_FAILURE_NONE -> FailureMode.NONE
        GrpcFailureMode.VALIDATION -> FailureMode.VALIDATION
        GrpcFailureMode.TIMEOUT -> FailureMode.TIMEOUT
        GrpcFailureMode.UNAVAILABLE -> FailureMode.UNAVAILABLE
        GrpcFailureMode.BUSINESS_CONFLICT -> FailureMode.BUSINESS_CONFLICT
        GrpcFailureMode.INTERNAL -> FailureMode.INTERNAL
        GrpcFailureMode.UNRECOGNIZED -> throw BackendException(
            code = ErrorCode.VALIDATION,
            message = "Unsupported gRPC failure mode: $this",
        )
    }
}

fun ScenarioType.toGrpcScenario(): GrpcScenarioType {
    return when (this) {
        ScenarioType.S1_SHORT_READ -> GrpcScenarioType.S1_SHORT_READ
        ScenarioType.S2_LARGE_READ -> GrpcScenarioType.S2_LARGE_READ
        ScenarioType.S3_PARTIAL_LARGE_READ -> GrpcScenarioType.S3_PARTIAL_LARGE_READ
        ScenarioType.S4_PAGE_READ -> GrpcScenarioType.S4_PAGE_READ
        ScenarioType.S5_SMALL_WRITE_ACK -> GrpcScenarioType.S5_SMALL_WRITE_ACK
        ScenarioType.S6_LARGE_WRITE_ACK -> GrpcScenarioType.S6_LARGE_WRITE_ACK
        ScenarioType.S7_EVENT_STREAM -> GrpcScenarioType.S7_EVENT_STREAM
        ScenarioType.S8_HEAVY_EVENT_STREAM -> GrpcScenarioType.S8_HEAVY_EVENT_STREAM
        ScenarioType.S9_LONG_SESSION -> GrpcScenarioType.S9_LONG_SESSION
    }
}

fun TransportType.toGrpcTransport(): GrpcTransportType {
    return when (this) {
        TransportType.REST -> GrpcTransportType.REST
        TransportType.SOAP -> GrpcTransportType.SOAP
        TransportType.GRAPHQL -> GrpcTransportType.GRAPHQL
        TransportType.WEBSOCKET -> GrpcTransportType.WEBSOCKET
        TransportType.GRPC -> GrpcTransportType.GRPC
    }
}

fun ScenarioResponse.toGrpcResponse(): GrpcScenarioResponse {
    return grpcScenarioResponse {
        requestId = this@toGrpcResponse.requestId
        correlationId = this@toGrpcResponse.correlationId
        sessionId = this@toGrpcResponse.sessionId.orEmpty()
        scenario = this@toGrpcResponse.scenario.toGrpcScenario()
        transport = this@toGrpcResponse.transport.toGrpcTransport()
        canonicalOperation = this@toGrpcResponse.canonicalOperation
        status = this@toGrpcResponse.status
        payloadSizeBytes = this@toGrpcResponse.payloadSizeBytes
        payloadChecksum = this@toGrpcResponse.payloadChecksum
        sequence = this@toGrpcResponse.sequence ?: 0
        acceptedAtEpochMs = this@toGrpcResponse.acceptedAtEpochMs
        completedAtEpochMs = this@toGrpcResponse.completedAtEpochMs
        serverProcessingTimeMs = this@toGrpcResponse.serverProcessingTimeMs
        serverProcessingTimeMicros = this@toGrpcResponse.serverProcessingTimeMicros
        payload = this@toGrpcResponse.payload.orEmpty()
        this@toGrpcResponse.document?.let { document = it.toGrpcDocument() }
        this@toGrpcResponse.preview?.let { preview = it.toGrpcPreview() }
        this@toGrpcResponse.page?.let { page = it.toGrpcPage() }
        this@toGrpcResponse.streamEvent?.let { streamEvent = it.toGrpcStreamEvent() }
        metadata.putAll(this@toGrpcResponse.metadata)
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadDocument.toGrpcDocument(): GrpcLargeReadDocument {
    return grpcLargeReadDocument {
        documentId = this@toGrpcDocument.documentId
        externalId = this@toGrpcDocument.externalId
        revision = this@toGrpcDocument.revision
        generatedAtEpochMs = this@toGrpcDocument.generatedAtEpochMs
        locale = this@toGrpcDocument.locale
        currency = this@toGrpcDocument.currency
        title = this@toGrpcDocument.title
        subtitle = this@toGrpcDocument.subtitle
        category = this@toGrpcDocument.category
        status = this@toGrpcDocument.status
        owner = this@toGrpcDocument.owner.toGrpcParty()
        contacts += this@toGrpcDocument.contacts.map { it.toGrpcContact() }
        tags += this@toGrpcDocument.tags
        flags += this@toGrpcDocument.flags
        attributes += this@toGrpcDocument.attributes.map { it.toGrpcAttribute() }
        parameterGroups += this@toGrpcDocument.parameterGroups.map { it.toGrpcParameterGroup() }
        lineItems += this@toGrpcDocument.lineItems.map { it.toGrpcLineItem() }
        relatedEntities += this@toGrpcDocument.relatedEntities.map { it.toGrpcRelatedEntity() }
        attachments += this@toGrpcDocument.attachments.map { it.toGrpcAttachment() }
        timeline += this@toGrpcDocument.timeline.map { it.toGrpcTimelineEntry() }
        metrics = this@toGrpcDocument.metrics.toGrpcMetrics()
        notes += this@toGrpcDocument.notes
        narrative = this@toGrpcDocument.narrative
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadParty.toGrpcParty(): GrpcLargeReadParty {
    return grpcLargeReadParty {
        partyId = this@toGrpcParty.partyId
        displayName = this@toGrpcParty.displayName
        role = this@toGrpcParty.role
        organization = this@toGrpcParty.organization
        segment = this@toGrpcParty.segment
        rating = this@toGrpcParty.rating
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadContact.toGrpcContact(): GrpcLargeReadContact {
    return grpcLargeReadContact {
        kind = this@toGrpcContact.kind
        label = this@toGrpcContact.label
        value = this@toGrpcContact.value
        preferred = this@toGrpcContact.preferred
        availability = this@toGrpcContact.availability
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadAttribute.toGrpcAttribute(): GrpcLargeReadAttribute {
    return grpcLargeReadAttribute {
        code = this@toGrpcAttribute.code
        name = this@toGrpcAttribute.name
        value = this@toGrpcAttribute.value
        unit = this@toGrpcAttribute.unit.orEmpty()
        category = this@toGrpcAttribute.category
        searchable = this@toGrpcAttribute.searchable
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadParameterGroup.toGrpcParameterGroup(): GrpcLargeReadParameterGroup {
    return grpcLargeReadParameterGroup {
        groupCode = this@toGrpcParameterGroup.groupCode
        groupTitle = this@toGrpcParameterGroup.groupTitle
        editable = this@toGrpcParameterGroup.editable
        parameters += this@toGrpcParameterGroup.parameters.map { it.toGrpcParameter() }
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadParameter.toGrpcParameter(): GrpcLargeReadParameter {
    return grpcLargeReadParameter {
        key = this@toGrpcParameter.key
        title = this@toGrpcParameter.title
        valueType = this@toGrpcParameter.valueType
        value = this@toGrpcParameter.value
        unit = this@toGrpcParameter.unit.orEmpty()
        required = this@toGrpcParameter.required
        source = this@toGrpcParameter.source
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadLineItem.toGrpcLineItem(): GrpcLargeReadLineItem {
    return grpcLargeReadLineItem {
        itemId = this@toGrpcLineItem.itemId
        sku = this@toGrpcLineItem.sku
        title = this@toGrpcLineItem.title
        category = this@toGrpcLineItem.category
        quantity = this@toGrpcLineItem.quantity
        unit = this@toGrpcLineItem.unit
        unitPrice = this@toGrpcLineItem.unitPrice
        totalPrice = this@toGrpcLineItem.totalPrice
        availabilityStatus = this@toGrpcLineItem.availabilityStatus
        tags += this@toGrpcLineItem.tags
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadRelatedEntity.toGrpcRelatedEntity(): GrpcLargeReadRelatedEntity {
    return grpcLargeReadRelatedEntity {
        entityId = this@toGrpcRelatedEntity.entityId
        relationType = this@toGrpcRelatedEntity.relationType
        title = this@toGrpcRelatedEntity.title
        status = this@toGrpcRelatedEntity.status
        priority = this@toGrpcRelatedEntity.priority
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadAttachment.toGrpcAttachment(): GrpcLargeReadAttachment {
    return grpcLargeReadAttachment {
        attachmentId = this@toGrpcAttachment.attachmentId
        fileName = this@toGrpcAttachment.fileName
        mimeType = this@toGrpcAttachment.mimeType
        sizeBytes = this@toGrpcAttachment.sizeBytes
        checksum = this@toGrpcAttachment.checksum
        sourceSystem = this@toGrpcAttachment.sourceSystem
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadTimelineEntry.toGrpcTimelineEntry(): GrpcLargeReadTimelineEntry {
    return grpcLargeReadTimelineEntry {
        eventCode = this@toGrpcTimelineEntry.eventCode
        title = this@toGrpcTimelineEntry.title
        actor = this@toGrpcTimelineEntry.actor
        occurredAtEpochMs = this@toGrpcTimelineEntry.occurredAtEpochMs
        status = this@toGrpcTimelineEntry.status
        description = this@toGrpcTimelineEntry.description
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadMetrics.toGrpcMetrics(): GrpcLargeReadMetrics {
    return grpcLargeReadMetrics {
        summaryScore = this@toGrpcMetrics.summaryScore
        riskScore = this@toGrpcMetrics.riskScore
        completenessPct = this@toGrpcMetrics.completenessPct
        freshnessHours = this@toGrpcMetrics.freshnessHours
        responseItems = this@toGrpcMetrics.responseItems
        attachmentBytes = this@toGrpcMetrics.attachmentBytes
        warnings = this@toGrpcMetrics.warnings
    }
}

private fun ru.meeweel.network_protocols_backend.domain.large_read.LargeReadPreview.toGrpcPreview(): GrpcLargeReadPreview {
    return grpcLargeReadPreview {
        documentId = this@toGrpcPreview.documentId
        title = this@toGrpcPreview.title
        status = this@toGrpcPreview.status
        primaryBadge = this@toGrpcPreview.primaryBadge
        summaryScore = this@toGrpcPreview.summaryScore
    }
}

private fun ru.meeweel.network_protocols_backend.domain.page_read.PageReadPage.toGrpcPage(): GrpcPageReadPage {
    return grpcPageReadPage {
        pageNumber = this@toGrpcPage.pageNumber
        pageSize = this@toGrpcPage.pageSize
        totalItems = this@toGrpcPage.totalItems
        nextCursor = this@toGrpcPage.nextCursor.orEmpty()
        sortBy = this@toGrpcPage.sortBy
        appliedFilters += this@toGrpcPage.appliedFilters
        summary = this@toGrpcPage.summary.toGrpcPageSummary()
        facets += this@toGrpcPage.facets.map { it.toGrpcFacet() }
        items += this@toGrpcPage.items.map { it.toGrpcPreview() }
    }
}

private fun ru.meeweel.network_protocols_backend.domain.page_read.PageReadSummary.toGrpcPageSummary(): GrpcPageReadSummary {
    return grpcPageReadSummary {
        totalAmount = this@toGrpcPageSummary.totalAmount
        selectedCount = this@toGrpcPageSummary.selectedCount
        highPriorityCount = this@toGrpcPageSummary.highPriorityCount
        staleCount = this@toGrpcPageSummary.staleCount
        warningCount = this@toGrpcPageSummary.warningCount
    }
}

private fun ru.meeweel.network_protocols_backend.domain.page_read.PageReadFacet.toGrpcFacet(): GrpcPageReadFacet {
    return grpcPageReadFacet {
        name = this@toGrpcFacet.name
        title = this@toGrpcFacet.title
        buckets += this@toGrpcFacet.buckets.map { it.toGrpcFacetBucket() }
    }
}

private fun ru.meeweel.network_protocols_backend.domain.page_read.PageReadFacetBucket.toGrpcFacetBucket(): GrpcPageReadFacetBucket {
    return grpcPageReadFacetBucket {
        value = this@toGrpcFacetBucket.value
        count = this@toGrpcFacetBucket.count
        selected = this@toGrpcFacetBucket.selected
    }
}

private fun ru.meeweel.network_protocols_backend.domain.stream_event.StreamEvent.toGrpcStreamEvent(): GrpcStreamEvent {
    return grpcStreamEvent {
        eventId = this@toGrpcStreamEvent.eventId
        eventType = this@toGrpcStreamEvent.eventType
        documentId = this@toGrpcStreamEvent.documentId
        emittedAtEpochMs = this@toGrpcStreamEvent.emittedAtEpochMs
        revision = this@toGrpcStreamEvent.revision
        priority = this@toGrpcStreamEvent.priority
        preview = this@toGrpcStreamEvent.preview.toGrpcPreview()
        changedFields += this@toGrpcStreamEvent.changedFields
        relatedItems += this@toGrpcStreamEvent.relatedItems.map { it.toGrpcPreview() }
        tags += this@toGrpcStreamEvent.tags
        notes += this@toGrpcStreamEvent.notes
        summary = this@toGrpcStreamEvent.summary.toGrpcStreamSummary()
    }
}

private fun ru.meeweel.network_protocols_backend.domain.stream_event.StreamEventSummary.toGrpcStreamSummary(): GrpcStreamEventSummary {
    return grpcStreamEventSummary {
        impactedItems = this@toGrpcStreamSummary.impactedItems
        warningCount = this@toGrpcStreamSummary.warningCount
        scoreDelta = this@toGrpcStreamSummary.scoreDelta
        currentStatus = this@toGrpcStreamSummary.currentStatus
    }
}
