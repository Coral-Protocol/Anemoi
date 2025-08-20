package org.coralprotocol.coralserver.mcpresources

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import kotlinx.coroutines.delay
import org.coralprotocol.coralserver.models.ResolvedThread
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import org.coralprotocol.coralserver.session.CoralAgentGraphSession

private suspend fun CoralAgentIndividualMcp.handler(request: ReadResourceRequest): ReadResourceResult {
    val renderedSessionStatus = this.coralAgentGraphSession.render(forParticipant = this.connectedAgentId)
    return ReadResourceResult(
        contents = listOf(
            TextResourceContents(
                text = renderedSessionStatus,
                uri = request.uri,
                mimeType = "application/xml",
            )
        )
    )
}

suspend fun CoralAgentGraphSession.render(forParticipant: String): String {
    if (forParticipant != "planning") {
        //TODO: Create better way of managing ordering / soft workflows
        // wait for a message to be sent before rendering the status
        while (this.getAllThreads().none { it.participants.contains(forParticipant) }) {
            delay(1000) // Wait for 1 second before checking again
        }

        // Let the planning agent send a message to the thread
        delay(10000)
    }
    val resolvedThreads: List<ResolvedThread> = this.getAllThreads().map { it.resolve() }
    if( resolvedThreads.isEmpty()) {
        return """
            <threads>
            You're not a participant in any threads. When you are, they will be listed here.
            </threads>
        """.trimIndent()
    }
    val participantThreads = resolvedThreads.filter { it.participants.contains(forParticipant) }
    val openParticipatingThreads = participantThreads.filter { !it.isClosed }
    val closedParticipatingThreads = participantThreads.filter { it.isClosed }
    val threadsNotParticipating = resolvedThreads.filter { !it.participants.contains(forParticipant) }
    // TODO: Add agent status:
    //  For resource-supporting agents:
    //   Not yet connected (should have been started by graph, but has not yet called this resource)
    //   Writing (last called this resource before any other interaction, with timeout before offline)
    //   Offline (last called this resource more than 5 minutes ago, or should have been woken from a mention but has not yet called this resource)
    //   Online (last called waitForMentions with a timeout that has not yet expired, and has not yet been notified of mentions)
    //  For non-resource-supporting agents:
    //   Not yet connected (should have been started by graph, but is not yet registered)
    //   Writing (Not within a waitForMentions holding)
    //   Offline (last called waitForMentions more than 5 minutes ago)
    //   Online (last called waitForMentions with a timeout that has not yet expired, and has not yet been notified of mentions)

    val status = """
        <messagingStatus>
        This is the current status of your messaging session. This section updates in real-time as messages are sent and received.
        You are working with these agents:
        <agents>
        ${
        this.getAllAgents(includeDebug = false).joinToString(separator = "\n") { agent ->
            """
            <agent>
                Name: ${agent.id}
                Description: ${agent.description}
            </agent>"""
        }
    }
        Here are the threads you are not part of:
        <threads>
        ${
        threadsNotParticipating.joinToString(separator = "\n") { thread ->
            """
                <thread>
                    <name>${thread.name}</name>
                    <id>${thread.id}</id>
                    <messages>
                        (omitted, you are not a participant. You may add yourself to the thread to see messages)
                    </messages>
                </thread>
"""
        }
    }
        </threads>
        Here are the threads you are participating in:
        <threads>
        ${/*Displau in more human readable format rather than pure xml */openParticipatingThreads.joinToString(separator = "\n") { thread ->
        """
                <thread>
                    <name>${thread.name}</name>
                    <participants>                    
                        ${
            thread.participants.joinToString(separator = ", ") { participant ->
                participant
            }
        }
                    </participants>
                    <id>${thread.id}</id> (Use this ID to send messages to the thread)
                    <messages>
                        ${
            thread.messages.sortedBy { it.timestamp }.joinToString(separator = "\n") { message ->
                with(message) {
                    val timestampFormatted = java.time.Instant.ofEpochMilli(timestamp).toString()
                    "$timestampFormatted [${senderId}] (Mentioning ${mentions.joinToString(", ")}) $content"
                }
            }
        }
                    </messages>
                </thread>
        Remember to add another agent as a participant to the thread if you want them to see the messages or if their input could be valuable.
"""

    }
    }
        ${/*Display in more human readable format rather than pure xml */closedParticipatingThreads.joinToString(
        separator = "\n"
    ) { thread ->
        """
                <thread>
                    <name>${thread.name}</name>
                    <id>${thread.id}</id>
                    <messages>
                       (omitted, thread is closed)
                    </messages>
                    <summary>
                        <messageCount>${thread.messages.size}</messageCount>
                        <summaryMessage>${thread.summary}</summaryMessage>
                    </summary>
                </thread>
"""
    }   
    }
        </threads>
"""
    return status.trimIndent()
}
fun CoralAgentIndividualMcp.addMessageResource() {
    addResource(
        name = "message",
        description = "Message resource",
        uri = this.connectedUri,
        mimeType = "application/json",
        readHandler = { request: ReadResourceRequest ->
            handler(request)
        },
    )
}
