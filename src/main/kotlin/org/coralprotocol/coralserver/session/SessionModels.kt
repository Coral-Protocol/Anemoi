package org.coralprotocol.coralserver.session

import com.chrynan.uri.core.UriString
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.EmptyContent.contentType
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import net.pwall.json.schema.JSONSchema
import org.coralprotocol.coralserver.orchestrator.AgentType
import org.coralprotocol.coralserver.orchestrator.runtime.AgentRuntime
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import java.net.URI
import kotlin.js.ExperimentalJsExport


private val logger = KotlinLogging.logger {}

/**
 * Data class for session creation request.
 */
@Serializable
data class CreateSessionRequest(
    val applicationId: String,
    val sessionId: String? = null,
    val privacyKey: String,
    val agentGraph: AgentGraphRequest?,
)

@Serializable
data class AgentGraphRequest(
    val agents: HashMap<AgentName, GraphAgentRequest>,
    val links: Set<Set<String>>,
    val tools: Map<String, CustomTool> = emptyMap(),
)

object JSONSchemaSerializer : KSerializer<JSONSchemaWithRaw> {
    // Serial names of descriptors should be unique, so choose app-specific name in case some library also would declare a serializer for Date.
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("org.coralprotocol.JSONSchemaWithRaw") {

    }

    override fun serialize(encoder: Encoder, value: JSONSchemaWithRaw) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("Can be serialized only as JSON")
        return json.encodeJsonElement(value.raw)
    }

    override fun deserialize(decoder: Decoder): JSONSchemaWithRaw {
        val jsonInput = decoder as? JsonDecoder ?: error("Can be deserialized only by JSON")
        val obj = jsonInput.decodeJsonElement().jsonObject;

        return JSONSchemaWithRaw(schema = JSONSchema.parse(obj.toString()), raw = obj)
    }
}

@Serializable(with = JSONSchemaSerializer::class)
data class JSONSchemaWithRaw(
    val schema: JSONSchema,
    val raw: JsonObject,
)

@Serializable
data class CustomTool(
    val transport: ToolTransport,
    val toolSchema: Tool,
)

fun CoralAgentIndividualMcp.addExtraTool(sessionId: String, agentId: String, tool: CustomTool) {
    addTool(
        name = tool.toolSchema.name,
        description = tool.toolSchema.description ?: "",
        inputSchema = tool.toolSchema.inputSchema,
    ) { request ->
        tool.transport.handleRequest(sessionId, agentId, request, tool.toolSchema)
    }
}


@Serializable
sealed interface ToolTransport {
    @SerialName("http")
    @Serializable
    data class Http(val url: UriString) : ToolTransport {
        override suspend fun handleRequest(
            sessionId: String,
            agentId: String,
            request: CallToolRequest,
            toolSchema: Tool
        ): CallToolResult {
            try {
                val client = HttpClient(CIO) {
                    install(ContentNegotiation) {
                        json()
                    }
                    engine {
                        requestTimeout = 0
                    }
                }

                val response = client.post(url.value) {
                    url {
                        appendPathSegments(sessionId, agentId)
                    }
                    contentType(ContentType.Application.Json)
                    setBody(request.arguments)
                }

                val body = response.bodyAsText()
                return CallToolResult(
                    content = listOf(TextContent(body))
                )
            } catch (ex: Exception) {
                logger.error(ex) { "Error occurred while executing request" }
                return CallToolResult(
                    isError = true,
                    content = listOf(TextContent("Error: $ex"))
                )
            }
        }
    }

    suspend fun handleRequest(
        sessionId: String,
        agentId: String,
        request: CallToolRequest,
        toolSchema: Tool
    ): CallToolResult
}

@Serializable
sealed interface GraphAgentRequest {
    val options: Map<String, JsonPrimitive>
    val systemPrompt: String?
    val blocking: Boolean?
    val tools: Set<String>

    @Serializable
    @SerialName("remote")
    data class Remote(
        val remote: AgentRuntime.Remote,
        override val options: Map<String, JsonPrimitive> = mapOf(),
        override val systemPrompt: String? = null,
        override val tools: Set<String> = setOf(),
        override val blocking: Boolean? = true
    ) :
        GraphAgentRequest

    @Serializable
    @SerialName("local")
    data class Local(
        val agentType: AgentType,
        override val options: Map<String, JsonPrimitive> = mapOf(),
        override val systemPrompt: String? = null,
        override val tools: Set<String> = setOf(),
        override val blocking: Boolean? = true
    ) :
        GraphAgentRequest
}

/**
 * Data class for session creation response.
 */
@Serializable
data class CreateSessionResponse(
    val sessionId: String,
    val applicationId: String,
    val privacyKey: String
)