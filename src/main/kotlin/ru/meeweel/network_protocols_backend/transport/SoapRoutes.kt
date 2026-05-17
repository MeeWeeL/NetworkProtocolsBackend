package ru.meeweel.network_protocols_backend.transport

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.io.StringReader
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import ru.meeweel.network_protocols_backend.domain.ErrorCode
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadAttachment
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadAttribute
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadContact
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadDocument
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadLineItem
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadMetrics
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadParameter
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadParameterGroup
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadParty
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadPreview
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadRelatedEntity
import ru.meeweel.network_protocols_backend.domain.large_read.LargeReadTimelineEntry
import ru.meeweel.network_protocols_backend.domain.page_read.PageReadFacet
import ru.meeweel.network_protocols_backend.domain.page_read.PageReadFacetBucket
import ru.meeweel.network_protocols_backend.domain.page_read.PageReadPage
import ru.meeweel.network_protocols_backend.domain.page_read.PageReadSummary
import ru.meeweel.network_protocols_backend.domain.ScenarioResponse
import ru.meeweel.network_protocols_backend.service.BackendException
import ru.meeweel.network_protocols_backend.service.ExperimentService
import ru.meeweel.network_protocols_backend.service.toHttpStatus

private const val SOAP_NS = "http://schemas.xmlsoap.org/soap/envelope/"
private const val SERVICE_NS = "urn:meeweel:network_protocols_backend"

fun Application.configureSoapRoutes(
    experimentService: ExperimentService,
) {
    routing {
        route("/api/soap") {
            get("/wsdl") {
                call.respondText(
                    text = soapWsdl(),
                    contentType = ContentType.Text.Xml,
                )
            }

            post {
                val requestBody = call.receiveText()
                val command = runCatching { parseSoapRequest(requestBody) }
                    .getOrElse { error ->
                        call.respondText(
                            text = soapFaultEnvelope(
                                faultCode = "soapenv:Client",
                                faultString = error.message ?: "invalid SOAP request",
                                correlationId = UUID.randomUUID().toString(),
                                errorCode = ErrorCode.VALIDATION.name,
                            ),
                            contentType = ContentType.Text.Xml,
                            status = HttpStatusCode.BadRequest,
                        )
                        return@post
                    }

                try {
                    val response = experimentService.executeSoap(command.scenario, command.request)
                    call.respondText(
                        text = soapResponseEnvelope(response, command.correlationId),
                        contentType = ContentType.Text.Xml,
                        status = HttpStatusCode.OK,
                    )
                } catch (error: BackendException) {
                    call.respondText(
                        text = soapFaultEnvelope(
                            faultCode = error.code.toSoapFaultCode(),
                            faultString = error.message ?: "SOAP scenario failed",
                            correlationId = command.correlationId,
                            errorCode = error.code.name,
                            scenario = command.scenario,
                        ),
                        contentType = ContentType.Text.Xml,
                        status = error.code.toHttpStatus(),
                    )
                }
            }
        }
    }
}

private fun parseSoapRequest(xml: String): SoapScenarioCommand {
    val document = secureDocumentBuilderFactory().newDocumentBuilder()
        .parse(InputSource(StringReader(xml)))
    val body = document.getElementsByTagNameNS(SOAP_NS, "Body").item(0) as? Element
        ?: error("SOAP Body not found")
    val requestElement = body.firstElementChild()
        ?: error("ExecuteScenarioRequest not found")

    val scenario = scenarioFromExternal(requestElement.childText("Scenario") ?: error("Scenario is required"))
    val correlationId = document.firstTextByLocalName("CorrelationId") ?: UUID.randomUUID().toString()
    val requestId = requestElement.childText("RequestId")
    val payloadSizeBytes = requestElement.childText("PayloadSizeBytes")?.toIntOrNull()
    val eventCount = requestElement.childText("EventCount")?.toIntOrNull() ?: 1
    val artificialDelayMs = requestElement.childText("ArtificialDelayMs")?.toLongOrNull() ?: 0L
    val sessionId = requestElement.childText("SessionId")
    val qClass = requestElement.childText("QClass")
    val loadProfile = requestElement.childText("LoadProfile")
    val failureMode = failureModeFromExternal(requestElement.childText("FailureMode"))
    val payload = requestElement.childText("Payload")
    val metadata = requestElement.metadataEntries()

    return SoapScenarioCommand(
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
            metadata = metadata + mapOf("adapter" to "SOAP"),
        ),
        correlationId = correlationId,
    )
}

private fun soapResponseEnvelope(
    response: ScenarioResponse,
    correlationId: String,
): String {
    return """
        |<soapenv:Envelope xmlns:soapenv="$SOAP_NS" xmlns:svc="$SERVICE_NS">
        |  <soapenv:Header>
        |    <svc:CorrelationId>$correlationId</svc:CorrelationId>
        |    <svc:Adapter>SOAP</svc:Adapter>
        |  </soapenv:Header>
        |  <soapenv:Body>
        |    <svc:ExecuteScenarioResponse>
        |      <svc:RequestId>${response.requestId}</svc:RequestId>
        |      ${response.sessionId?.let { "<svc:SessionId>${xmlEscape(it)}</svc:SessionId>" } ?: ""}
        |      <svc:Scenario>${scenarioCode(response.scenario)}</svc:Scenario>
        |      <svc:CanonicalOperation>${xmlEscape(response.canonicalOperation)}</svc:CanonicalOperation>
        |      <svc:Status>${response.status}</svc:Status>
        |      <svc:Transport>${response.transport.name}</svc:Transport>
        |      <svc:PayloadSizeBytes>${response.payloadSizeBytes}</svc:PayloadSizeBytes>
        |      <svc:PayloadChecksum>${response.payloadChecksum}</svc:PayloadChecksum>
        |      ${response.sequence?.let { "<svc:Sequence>$it</svc:Sequence>" } ?: ""}
        |      <svc:AcceptedAtEpochMs>${response.acceptedAtEpochMs}</svc:AcceptedAtEpochMs>
        |      <svc:CompletedAtEpochMs>${response.completedAtEpochMs}</svc:CompletedAtEpochMs>
        |      <svc:ServerProcessingTimeMs>${response.serverProcessingTimeMs}</svc:ServerProcessingTimeMs>
        |      <svc:ServerProcessingTimeMicros>${response.serverProcessingTimeMicros}</svc:ServerProcessingTimeMicros>
        |      <svc:ProtocolAdapter>SOAP</svc:ProtocolAdapter>
        |      ${metadataEnvelope(response.metadata)}
        |      ${response.payload?.let { "<svc:Payload>${xmlEscape(it)}</svc:Payload>" } ?: ""}
        |      ${response.document?.let(::documentEnvelope) ?: ""}
        |      ${response.preview?.let(::previewEnvelope) ?: ""}
        |      ${response.page?.let(::pageEnvelope) ?: ""}
        |    </svc:ExecuteScenarioResponse>
        |  </soapenv:Body>
        |</soapenv:Envelope>
    """.trimMargin()
}

private fun soapFaultEnvelope(
    faultCode: String,
    faultString: String,
    correlationId: String,
    errorCode: String? = null,
    scenario: ru.meeweel.network_protocols_backend.domain.ScenarioType? = null,
): String {
    return """
        |<soapenv:Envelope xmlns:soapenv="$SOAP_NS" xmlns:svc="$SERVICE_NS">
        |  <soapenv:Header>
        |    <svc:CorrelationId>$correlationId</svc:CorrelationId>
        |    <svc:Adapter>SOAP</svc:Adapter>
        |  </soapenv:Header>
        |  <soapenv:Body>
        |    <soapenv:Fault>
        |      <faultcode>$faultCode</faultcode>
        |      <faultstring>${xmlEscape(faultString)}</faultstring>
        |      <detail>
        |        <svc:ProtocolAdapter>SOAP</svc:ProtocolAdapter>
        |        ${errorCode?.let { "<svc:ErrorCode>${xmlEscape(it)}</svc:ErrorCode>" } ?: ""}
        |        ${scenario?.let { "<svc:Scenario>${scenarioCode(it)}</svc:Scenario>" } ?: ""}
        |      </detail>
        |    </soapenv:Fault>
        |  </soapenv:Body>
        |</soapenv:Envelope>
    """.trimMargin()
}

private fun soapWsdl(): String {
    return """
        |<definitions name="ExperimentService"
        |             targetNamespace="$SERVICE_NS"
        |             xmlns:tns="$SERVICE_NS"
        |             xmlns:soap="http://schemas.xmlsoap.org/wsdl/soap/"
        |             xmlns:xsd="http://www.w3.org/2001/XMLSchema"
        |             xmlns:wsdl="http://schemas.xmlsoap.org/wsdl/">
        |  <wsdl:types>
        |    <xsd:schema targetNamespace="$SERVICE_NS">
        |      <xsd:element name="ExecuteScenarioRequest" type="xsd:string"/>
        |      <xsd:element name="ExecuteScenarioResponse" type="xsd:string"/>
        |    </xsd:schema>
        |  </wsdl:types>
        |  <wsdl:message name="ExecuteScenarioRequest">
        |    <wsdl:part name="parameters" element="tns:ExecuteScenarioRequest"/>
        |  </wsdl:message>
        |  <wsdl:message name="ExecuteScenarioResponse">
        |    <wsdl:part name="parameters" element="tns:ExecuteScenarioResponse"/>
        |  </wsdl:message>
        |</definitions>
    """.trimMargin()
}

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory {
    return DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    }
}

private fun org.w3c.dom.Document.firstTextByLocalName(localName: String): String? {
    val nodes = getElementsByTagNameNS(SERVICE_NS, localName)
    if (nodes.length == 0) return null
    return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
}

private fun Element.childText(localName: String): String? {
    val nodes = getElementsByTagNameNS(SERVICE_NS, localName)
    if (nodes.length == 0) return null
    return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
}

private fun Element.metadataEntries(): Map<String, String> {
    val metadataElement = firstDirectChild("Metadata") ?: return emptyMap()
    val entries = linkedMapOf<String, String>()
    val nodes = metadataElement.getElementsByTagNameNS(SERVICE_NS, "Entry")
    for (index in 0 until nodes.length) {
        val node = nodes.item(index) as? Element ?: continue
        val key = node.getAttribute("key").trim()
        if (key.isNotBlank()) {
            entries[key] = node.textContent?.trim().orEmpty()
        }
    }
    return entries
}

private fun Element.firstElementChild(): Element? {
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child is Element) return child
    }
    return null
}

private fun Element.firstDirectChild(localName: String): Element? {
    val children = childNodes
    for (index in 0 until children.length) {
        val child = children.item(index)
        if (child is Element && child.localName == localName && child.namespaceURI == SERVICE_NS) {
            return child
        }
    }
    return null
}

private fun metadataEnvelope(metadata: Map<String, String>): String {
    if (metadata.isEmpty()) return ""
    val entries = metadata.entries.joinToString(separator = "") { (key, value) ->
        "<svc:Entry key=\"${xmlEscape(key)}\">${xmlEscape(value)}</svc:Entry>"
    }
    return "<svc:Metadata>$entries</svc:Metadata>"
}

private fun documentEnvelope(document: LargeReadDocument): String {
    return """
        |<svc:Document>
        |  <svc:DocumentId>${xmlEscape(document.documentId)}</svc:DocumentId>
        |  <svc:ExternalId>${xmlEscape(document.externalId)}</svc:ExternalId>
        |  <svc:Revision>${document.revision}</svc:Revision>
        |  <svc:GeneratedAtEpochMs>${document.generatedAtEpochMs}</svc:GeneratedAtEpochMs>
        |  <svc:Locale>${xmlEscape(document.locale)}</svc:Locale>
        |  <svc:Currency>${xmlEscape(document.currency)}</svc:Currency>
        |  <svc:Title>${xmlEscape(document.title)}</svc:Title>
        |  <svc:Subtitle>${xmlEscape(document.subtitle)}</svc:Subtitle>
        |  <svc:Category>${xmlEscape(document.category)}</svc:Category>
        |  <svc:Status>${xmlEscape(document.status)}</svc:Status>
        |  ${partyEnvelope(document.owner)}
        |  ${contactsEnvelope(document.contacts)}
        |  ${stringListEnvelope("Tags", "Tag", document.tags)}
        |  ${stringListEnvelope("Flags", "Flag", document.flags)}
        |  ${attributesEnvelope(document.attributes)}
        |  ${parameterGroupsEnvelope(document.parameterGroups)}
        |  ${lineItemsEnvelope(document.lineItems)}
        |  ${relatedEntitiesEnvelope(document.relatedEntities)}
        |  ${attachmentsEnvelope(document.attachments)}
        |  ${timelineEnvelope(document.timeline)}
        |  ${metricsEnvelope(document.metrics)}
        |  ${stringListEnvelope("Notes", "Note", document.notes)}
        |  <svc:Narrative>${xmlEscape(document.narrative)}</svc:Narrative>
        |</svc:Document>
    """.trimMargin()
}

private fun pageEnvelope(page: PageReadPage): String {
    return """
        |<svc:Page>
        |  <svc:PageNumber>${page.pageNumber}</svc:PageNumber>
        |  <svc:PageSize>${page.pageSize}</svc:PageSize>
        |  <svc:TotalItems>${page.totalItems}</svc:TotalItems>
        |  ${page.nextCursor?.let { "<svc:NextCursor>${xmlEscape(it)}</svc:NextCursor>" } ?: ""}
        |  <svc:SortBy>${xmlEscape(page.sortBy)}</svc:SortBy>
        |  ${stringListEnvelope("AppliedFilters", "Filter", page.appliedFilters)}
        |  ${pageSummaryEnvelope(page.summary)}
        |  ${pageFacetsEnvelope(page.facets)}
        |  ${pageItemsEnvelope(page.items)}
        |</svc:Page>
    """.trimMargin()
}

private fun pageSummaryEnvelope(summary: PageReadSummary): String {
    return """
        |<svc:Summary>
        |  <svc:TotalAmount>${summary.totalAmount}</svc:TotalAmount>
        |  <svc:SelectedCount>${summary.selectedCount}</svc:SelectedCount>
        |  <svc:HighPriorityCount>${summary.highPriorityCount}</svc:HighPriorityCount>
        |  <svc:StaleCount>${summary.staleCount}</svc:StaleCount>
        |  <svc:WarningCount>${summary.warningCount}</svc:WarningCount>
        |</svc:Summary>
    """.trimMargin()
}

private fun pageFacetsEnvelope(facets: List<PageReadFacet>): String {
    if (facets.isEmpty()) return ""
    val body = facets.joinToString(separator = "") { facet ->
        """
            |<svc:Facet>
            |  <svc:Name>${xmlEscape(facet.name)}</svc:Name>
            |  <svc:Title>${xmlEscape(facet.title)}</svc:Title>
            |  ${pageFacetBucketsEnvelope(facet.buckets)}
            |</svc:Facet>
        """.trimMargin()
    }
    return "<svc:Facets>$body</svc:Facets>"
}

private fun pageFacetBucketsEnvelope(buckets: List<PageReadFacetBucket>): String {
    if (buckets.isEmpty()) return ""
    val body = buckets.joinToString(separator = "") { bucket ->
        """
            |<svc:Bucket>
            |  <svc:Value>${xmlEscape(bucket.value)}</svc:Value>
            |  <svc:Count>${bucket.count}</svc:Count>
            |  <svc:Selected>${bucket.selected}</svc:Selected>
            |</svc:Bucket>
        """.trimMargin()
    }
    return "<svc:Buckets>$body</svc:Buckets>"
}

private fun pageItemsEnvelope(items: List<LargeReadPreview>): String {
    if (items.isEmpty()) return ""
    val body = items.joinToString(separator = "") { item ->
        """
            |<svc:Item>
            |  <svc:DocumentId>${xmlEscape(item.documentId)}</svc:DocumentId>
            |  <svc:Title>${xmlEscape(item.title)}</svc:Title>
            |  <svc:Status>${xmlEscape(item.status)}</svc:Status>
            |  <svc:PrimaryBadge>${xmlEscape(item.primaryBadge)}</svc:PrimaryBadge>
            |  <svc:SummaryScore>${item.summaryScore}</svc:SummaryScore>
            |</svc:Item>
        """.trimMargin()
    }
    return "<svc:Items>$body</svc:Items>"
}

private fun partyEnvelope(party: LargeReadParty): String {
    return """
        |<svc:Owner>
        |  <svc:PartyId>${xmlEscape(party.partyId)}</svc:PartyId>
        |  <svc:DisplayName>${xmlEscape(party.displayName)}</svc:DisplayName>
        |  <svc:Role>${xmlEscape(party.role)}</svc:Role>
        |  <svc:Organization>${xmlEscape(party.organization)}</svc:Organization>
        |  <svc:Segment>${xmlEscape(party.segment)}</svc:Segment>
        |  <svc:Rating>${party.rating}</svc:Rating>
        |</svc:Owner>
    """.trimMargin()
}

private fun contactsEnvelope(contacts: List<LargeReadContact>): String {
    if (contacts.isEmpty()) return ""
    val body = contacts.joinToString(separator = "") { contact ->
        """
            |<svc:Contact>
            |  <svc:Kind>${xmlEscape(contact.kind)}</svc:Kind>
            |  <svc:Label>${xmlEscape(contact.label)}</svc:Label>
            |  <svc:Value>${xmlEscape(contact.value)}</svc:Value>
            |  <svc:Preferred>${contact.preferred}</svc:Preferred>
            |  <svc:Availability>${xmlEscape(contact.availability)}</svc:Availability>
            |</svc:Contact>
        """.trimMargin()
    }
    return "<svc:Contacts>$body</svc:Contacts>"
}

private fun attributesEnvelope(attributes: List<LargeReadAttribute>): String {
    if (attributes.isEmpty()) return ""
    val body = attributes.joinToString(separator = "") { attribute ->
        """
            |<svc:Attribute>
            |  <svc:Code>${xmlEscape(attribute.code)}</svc:Code>
            |  <svc:Name>${xmlEscape(attribute.name)}</svc:Name>
            |  <svc:Value>${xmlEscape(attribute.value)}</svc:Value>
            |  ${attribute.unit?.let { "<svc:Unit>${xmlEscape(it)}</svc:Unit>" } ?: ""}
            |  <svc:Category>${xmlEscape(attribute.category)}</svc:Category>
            |  <svc:Searchable>${attribute.searchable}</svc:Searchable>
            |</svc:Attribute>
        """.trimMargin()
    }
    return "<svc:Attributes>$body</svc:Attributes>"
}

private fun parameterGroupsEnvelope(groups: List<LargeReadParameterGroup>): String {
    if (groups.isEmpty()) return ""
    val body = groups.joinToString(separator = "") { group ->
        val parameters = group.parameters.joinToString(separator = "") { parameter ->
            parameterEnvelope(parameter)
        }
        """
            |<svc:ParameterGroup>
            |  <svc:GroupCode>${xmlEscape(group.groupCode)}</svc:GroupCode>
            |  <svc:GroupTitle>${xmlEscape(group.groupTitle)}</svc:GroupTitle>
            |  <svc:Editable>${group.editable}</svc:Editable>
            |  <svc:Parameters>$parameters</svc:Parameters>
            |</svc:ParameterGroup>
        """.trimMargin()
    }
    return "<svc:ParameterGroups>$body</svc:ParameterGroups>"
}

private fun parameterEnvelope(parameter: LargeReadParameter): String {
    return """
        |<svc:Parameter>
        |  <svc:Key>${xmlEscape(parameter.key)}</svc:Key>
        |  <svc:Title>${xmlEscape(parameter.title)}</svc:Title>
        |  <svc:ValueType>${xmlEscape(parameter.valueType)}</svc:ValueType>
        |  <svc:Value>${xmlEscape(parameter.value)}</svc:Value>
        |  ${parameter.unit?.let { "<svc:Unit>${xmlEscape(it)}</svc:Unit>" } ?: ""}
        |  <svc:Required>${parameter.required}</svc:Required>
        |  <svc:Source>${xmlEscape(parameter.source)}</svc:Source>
        |</svc:Parameter>
    """.trimMargin()
}

private fun lineItemsEnvelope(lineItems: List<LargeReadLineItem>): String {
    if (lineItems.isEmpty()) return ""
    val body = lineItems.joinToString(separator = "") { item ->
        """
            |<svc:LineItem>
            |  <svc:ItemId>${xmlEscape(item.itemId)}</svc:ItemId>
            |  <svc:Sku>${xmlEscape(item.sku)}</svc:Sku>
            |  <svc:Title>${xmlEscape(item.title)}</svc:Title>
            |  <svc:Category>${xmlEscape(item.category)}</svc:Category>
            |  <svc:Quantity>${item.quantity}</svc:Quantity>
            |  <svc:Unit>${xmlEscape(item.unit)}</svc:Unit>
            |  <svc:UnitPrice>${item.unitPrice}</svc:UnitPrice>
            |  <svc:TotalPrice>${item.totalPrice}</svc:TotalPrice>
            |  <svc:AvailabilityStatus>${xmlEscape(item.availabilityStatus)}</svc:AvailabilityStatus>
            |  ${stringListEnvelope("Tags", "Tag", item.tags)}
            |</svc:LineItem>
        """.trimMargin()
    }
    return "<svc:LineItems>$body</svc:LineItems>"
}

private fun relatedEntitiesEnvelope(entities: List<LargeReadRelatedEntity>): String {
    if (entities.isEmpty()) return ""
    val body = entities.joinToString(separator = "") { entity ->
        """
            |<svc:RelatedEntity>
            |  <svc:EntityId>${xmlEscape(entity.entityId)}</svc:EntityId>
            |  <svc:RelationType>${xmlEscape(entity.relationType)}</svc:RelationType>
            |  <svc:Title>${xmlEscape(entity.title)}</svc:Title>
            |  <svc:Status>${xmlEscape(entity.status)}</svc:Status>
            |  <svc:Priority>${xmlEscape(entity.priority)}</svc:Priority>
            |</svc:RelatedEntity>
        """.trimMargin()
    }
    return "<svc:RelatedEntities>$body</svc:RelatedEntities>"
}

private fun attachmentsEnvelope(attachments: List<LargeReadAttachment>): String {
    if (attachments.isEmpty()) return ""
    val body = attachments.joinToString(separator = "") { attachment ->
        """
            |<svc:Attachment>
            |  <svc:AttachmentId>${xmlEscape(attachment.attachmentId)}</svc:AttachmentId>
            |  <svc:FileName>${xmlEscape(attachment.fileName)}</svc:FileName>
            |  <svc:MimeType>${xmlEscape(attachment.mimeType)}</svc:MimeType>
            |  <svc:SizeBytes>${attachment.sizeBytes}</svc:SizeBytes>
            |  <svc:Checksum>${xmlEscape(attachment.checksum)}</svc:Checksum>
            |  <svc:SourceSystem>${xmlEscape(attachment.sourceSystem)}</svc:SourceSystem>
            |</svc:Attachment>
        """.trimMargin()
    }
    return "<svc:Attachments>$body</svc:Attachments>"
}

private fun timelineEnvelope(entries: List<LargeReadTimelineEntry>): String {
    if (entries.isEmpty()) return ""
    val body = entries.joinToString(separator = "") { entry ->
        """
            |<svc:TimelineEntry>
            |  <svc:EventCode>${xmlEscape(entry.eventCode)}</svc:EventCode>
            |  <svc:Title>${xmlEscape(entry.title)}</svc:Title>
            |  <svc:Actor>${xmlEscape(entry.actor)}</svc:Actor>
            |  <svc:OccurredAtEpochMs>${entry.occurredAtEpochMs}</svc:OccurredAtEpochMs>
            |  <svc:Status>${xmlEscape(entry.status)}</svc:Status>
            |  <svc:Description>${xmlEscape(entry.description)}</svc:Description>
            |</svc:TimelineEntry>
        """.trimMargin()
    }
    return "<svc:Timeline>$body</svc:Timeline>"
}

private fun metricsEnvelope(metrics: LargeReadMetrics): String {
    return """
        |<svc:Metrics>
        |  <svc:SummaryScore>${metrics.summaryScore}</svc:SummaryScore>
        |  <svc:RiskScore>${metrics.riskScore}</svc:RiskScore>
        |  <svc:CompletenessPct>${metrics.completenessPct}</svc:CompletenessPct>
        |  <svc:FreshnessHours>${metrics.freshnessHours}</svc:FreshnessHours>
        |  <svc:ResponseItems>${metrics.responseItems}</svc:ResponseItems>
        |  <svc:AttachmentBytes>${metrics.attachmentBytes}</svc:AttachmentBytes>
        |  <svc:Warnings>${metrics.warnings}</svc:Warnings>
        |</svc:Metrics>
    """.trimMargin()
}

private fun previewEnvelope(preview: LargeReadPreview): String {
    return """
        |<svc:Preview>
        |  <svc:DocumentId>${xmlEscape(preview.documentId)}</svc:DocumentId>
        |  <svc:Title>${xmlEscape(preview.title)}</svc:Title>
        |  <svc:Status>${xmlEscape(preview.status)}</svc:Status>
        |  <svc:PrimaryBadge>${xmlEscape(preview.primaryBadge)}</svc:PrimaryBadge>
        |  <svc:SummaryScore>${preview.summaryScore}</svc:SummaryScore>
        |</svc:Preview>
    """.trimMargin()
}

private fun stringListEnvelope(
    parentName: String,
    childName: String,
    values: List<String>,
): String {
    if (values.isEmpty()) return ""
    val body = values.joinToString(separator = "") { value ->
        "<svc:$childName>${xmlEscape(value)}</svc:$childName>"
    }
    return "<svc:$parentName>$body</svc:$parentName>"
}

private fun xmlEscape(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private fun ErrorCode.toSoapFaultCode(): String {
    return when (this) {
        ErrorCode.VALIDATION -> "soapenv:Client"
        ErrorCode.TIMEOUT,
        ErrorCode.UNAVAILABLE,
        ErrorCode.BUSINESS_CONFLICT,
        ErrorCode.INTERNAL,
        -> "soapenv:Server"
    }
}
