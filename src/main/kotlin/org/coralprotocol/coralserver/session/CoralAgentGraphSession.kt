package org.coralprotocol.coralserver.session

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.util.collections.*
import kotlinx.coroutines.CompletableDeferred
import org.coralprotocol.coralserver.EventBus
import org.coralprotocol.coralserver.models.Agent
import org.coralprotocol.coralserver.models.Message
import org.coralprotocol.coralserver.models.Thread
import org.coralprotocol.coralserver.models.resolve
import org.coralprotocol.coralserver.server.CoralAgentIndividualMcp
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Session class to hold stateful information for a specific application and privacy key.
 * [devRequiredAgentStartCount] is the number of agents that need to register before the session can proceed. This is for devmode only.
 */
class CoralAgentGraphSession(
    val id: String,
    val applicationId: String,
    val privacyKey: String,
    val agentGraph: AgentGraph?,
    val coralAgentConnections: MutableList<CoralAgentIndividualMcp> = mutableListOf(),
    val groups: List<Set<String>> = listOf(),
    var devRequiredAgentStartCount: Int = 0,
) {
    private val agents = ConcurrentHashMap<String, Agent>()
    private val debugAgents = ConcurrentSet<String>()

    private val threads = ConcurrentHashMap<String, Thread>()

    private val agentNotifications = ConcurrentHashMap<String, CompletableDeferred<List<Message>>>()
    private val agentSenderNotifications = ConcurrentHashMap<Pair<String, List<String>>, CompletableDeferred<List<Message>>>()

    private val lastReadMessageIndex = ConcurrentHashMap<Pair<String, String>, Int>()

    private val agentGroupScheduler = GroupScheduler(groups)
    private val countBasedScheduler = CountBasedScheduler()

    private val eventBus = EventBus<Event>()
    val events get() = eventBus.events


    fun getAllThreadsAgentParticipatesIn(agentId: String): List<Thread> {
        return threads.values.filter { it.participants.contains(agentId) }
    }

    fun clearAll() {
        agents.clear()
        threads.clear()
        agentNotifications.clear()
        lastReadMessageIndex.clear()
        countBasedScheduler.clear()
        agentGroupScheduler.clear()
    }

    fun registerAgent(agentId: String, agentUri: String? = null, agentDescription: String? = null, force: Boolean = false): Agent? {
        if (agents.containsKey(agentId)) {
            logger.warn { "$agentId has already been registered" }
            if (!force) {
                return null;
            }
        }
        val agent = Agent(
            id = agentId,
            description = agentDescription ?: "",
            extraTools = agentGraph?.let {
                it.agents[AgentName(agentId)]?.extraTools?.mapNotNull { tool -> it.tools[tool] }?.toSet()
            } ?: setOf(),
            mcpUrl = agentUri
        )
        agents[agent.id] = agent

        agentGroupScheduler.registerAgent(agent.id)
        countBasedScheduler.registerAgent(agent.id)
        eventBus.emit(Event.AgentRegistered(agent))
        return agent
    }

    fun getRegisteredAgentsCount(): Int = countBasedScheduler.getRegisteredAgentsCount()

    suspend fun waitForGroup(agentId: String, timeoutMs: Long): Boolean =
        agentGroupScheduler.waitForGroup(agentId, timeoutMs)

    suspend fun waitForAgentCount(targetCount: Int, timeoutMs: Long): Boolean =
        countBasedScheduler.waitForAgentCount(targetCount, timeoutMs)

    fun getAgent(agentId: String): Agent? = agents[agentId]

    fun getAllAgents(includeDebug: Boolean = false): List<Agent> = when (includeDebug) {
        true -> agents.values.toList()
        false -> agents.values.filter { !debugAgents.contains(it.id) }
    }

    fun getAllThreads(): List<Thread> = threads.values.toList()

    fun registerDebugAgent(): Agent {
        val id = UUID.randomUUID().toString()
        if (agents[id] !== null) throw AssertionError("Debug agent id collision")
        val agent = Agent(id = id, description = "", mcpUrl = "n/a")
        agents[id] = agent
        debugAgents.add(id)
        return agent
    }

    fun createThread(name: String, creatorId: String, participantIds: List<String>): Thread {
        if (creatorId != "debug" && !agents.containsKey(creatorId)) {
            throw IllegalArgumentException("Creator agent $creatorId not found")
        }

        val validParticipants = participantIds.filter { agents.containsKey(it) }.toMutableList()

        if (!validParticipants.contains(creatorId)) {
            validParticipants.add(creatorId)
        }

        val thread = Thread(
            name = name,
            creatorId = creatorId,
            participants = validParticipants
        )
        threads[thread.id] = thread

        eventBus.emit(
            Event.ThreadCreated(
                id = thread.id,
                name = name,
                creatorId = creatorId,
                participants = validParticipants,
                summary = null
            )
        )
        return thread
    }

    fun getThread(threadId: String): Thread? = threads[threadId]

    fun getThreadsForAgent(agentId: String): List<Thread> {
        return threads.values.filter { it.participants.contains(agentId) }
    }

    fun addParticipantToThread(threadId: String, participantId: String): Boolean {
        val thread = threads[threadId] ?: return false
        val agent = agents[participantId] ?: return false

        if (thread.isClosed) return false

        if (!thread.participants.contains(participantId)) {
            thread.participants.add(participantId)
            lastReadMessageIndex[Pair(participantId, threadId)] = 0
        }

        logger.info { "Adding $participantId to $thread" }
        return true
    }

    fun removeParticipantFromThread(threadId: String, participantId: String): Boolean {
        val thread = threads[threadId] ?: return false

        if (thread.isClosed) return false

        return thread.participants.remove(participantId)
    }

    fun closeThread(threadId: String, summary: String): Boolean {
        val thread = threads[threadId] ?: return false

        thread.isClosed = true
        thread.summary = summary

        return true
    }

    fun getColorForSenderId(senderId: String): String {
        val colors = listOf(
            "#FF5733", "#33FF57", "#3357FF", "#F3FF33", "#FF33F3",
            "#33FFF3", "#FF8033", "#8033FF", "#33FF80", "#FF3380"
        )
        val hash = senderId.hashCode()
        val index = Math.abs(hash) % colors.size
        return colors[index]
    }

    fun sendMessage(
        threadId: String,
        senderId: String,
        content: String,
        mentions: List<String> = emptyList()
    ): Message {
        val thread = getThread(threadId) ?: throw IllegalArgumentException("Thread with id $threadId not found")
        val sender = getAgent(senderId) ?: throw IllegalArgumentException("Agent with id $senderId not found")

        val message = Message.create(thread, sender, content, mentions)
        thread.messages.add(message)
        eventBus.emit(Event.MessageSent(threadId, message.resolve()))
        notifyMentionedAgents(message)
        return message
    }

    private fun notifyMentionedAgents(message: Message) {
        if (message.sender.id == "system") {
            val thread = threads[message.thread.id] ?: return
            thread.participants.forEach { participantId ->
                val deferred = agentNotifications[participantId]
                if (deferred != null && !deferred.isCompleted) {
                    deferred.complete(listOf(message))
                }
            }
            return
        }

        message.mentions.forEach { mentionId ->
            val deferred = agentNotifications[mentionId]
            if (deferred != null && !deferred.isCompleted) {
                deferred.complete(listOf(message))
            }
        }

        // Notify agents waiting for messages from specific senders
        val senderId = message.sender.id
        val thread = threads[message.thread.id] ?: return

        // For each participant in the thread
        thread.participants.forEach { participantId ->
            // Check if they're waiting for messages from this sender
            agentSenderNotifications.keys
                .filter { it.first == participantId && it.second.contains(senderId) }
                .forEach { key ->
                    val deferred = agentSenderNotifications[key]
                    if (deferred != null && !deferred.isCompleted) {
                        deferred.complete(listOf(message))
                    }
                }
        }
    }

    suspend fun waitForMentions(agentId: String, timeoutMs: Long): List<Message> {
        if (timeoutMs <= 0) {
            throw IllegalArgumentException("Timeout must be greater than 0")
        }

        val agent = agents[agentId] ?: return emptyList()

        val unreadMessages = getUnreadMessagesForAgent(agentId)
        if (unreadMessages.isNotEmpty()) {
            updateLastReadIndices(agentId, unreadMessages)
            return unreadMessages
        }

        val deferred = CompletableDeferred<List<Message>>()
        agentNotifications[agentId] = deferred

        val result = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            val startTime = System.currentTimeMillis()
            val res = deferred.await()
            val elapsedTime = System.currentTimeMillis() - startTime
            logger.info { "Waited for mentions for agent $agentId for $elapsedTime ms" }
            res
        } ?: emptyList()

        agentNotifications.remove(agentId)

        updateLastReadIndices(agentId, result)

        return result
    }

    /**
     * Wait for messages from specific agents in any thread the agent participates in.
     * 
     * @param agentId The ID of the agent waiting for messages
     * @param fromAgentIds The IDs of the agents to wait for messages from
     * @param timeoutMs The maximum time to wait in milliseconds
     * @return A list of messages from the specified agents
     */
    suspend fun waitForAgentMessages(agentId: String, fromAgentIds: List<String>, timeoutMs: Long): List<Message> {
        if (timeoutMs <= 0) {
            throw IllegalArgumentException("Timeout must be greater than 0")
        }

        val agent = agents[agentId] ?: return emptyList()

        // Check if there are any unread messages from the specified agents
        val unreadMessages = getUnreadMessagesFromAgents(agentId, fromAgentIds)
        if (unreadMessages.isNotEmpty()) {
            updateLastReadIndices(agentId, unreadMessages)
            return unreadMessages
        }

        // If no unread messages, wait for new messages
        val deferred = CompletableDeferred<List<Message>>()
        agentSenderNotifications[Pair(agentId, fromAgentIds)] = deferred

        val result = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            val startTime = System.currentTimeMillis()
            val res = deferred.await()
            val elapsedTime = System.currentTimeMillis() - startTime
            logger.info { "Waited for messages from agents $fromAgentIds for agent $agentId for $elapsedTime ms" }
            res
        } ?: emptyList()

        agentSenderNotifications.remove(Pair(agentId, fromAgentIds))

        updateLastReadIndices(agentId, result)

        return result
    }

    /**
     * Get unread messages from specific agents in any thread the agent participates in.
     * 
     * @param agentId The ID of the agent to get unread messages for
     * @param fromAgentIds The IDs of the agents to get messages from
     * @return A list of unread messages from the specified agents
     */
    fun getUnreadMessagesFromAgents(agentId: String, fromAgentIds: List<String>): List<Message> {
        val agent = agents[agentId] ?: return emptyList()

        val result = mutableListOf<Message>()

        val agentThreads = getThreadsForAgent(agentId)

        for (thread in agentThreads) {
            val lastReadIndex = lastReadMessageIndex[Pair(agentId, thread.id)] ?: 0

            val unreadMessages = thread.messages.subList(lastReadIndex, thread.messages.size)

            result.addAll(unreadMessages.filter {
                fromAgentIds.contains(it.sender.id)
            })
        }

        return result
    }

    fun getUnreadMessagesForAgent(agentId: String): List<Message> {
        val agent = agents[agentId] ?: return emptyList()

        val result = mutableListOf<Message>()

        val agentThreads = getThreadsForAgent(agentId)

        for (thread in agentThreads) {
            val lastReadIndex = lastReadMessageIndex[Pair(agentId, thread.id)] ?: 0

            val unreadMessages = thread.messages.subList(lastReadIndex, thread.messages.size)

            result.addAll(unreadMessages.filter {
                it.mentions.contains(agentId) || it.sender.id == "system"
            })
        }

        return result
    }

    fun updateLastReadIndices(agentId: String, messages: List<Message>) {
        val messagesByThread = messages.groupBy { it.thread }

        for ((thread, threadMessages) in messagesByThread) {
            val messageIndices = threadMessages.map { thread.messages.indexOf(it) }
            if (messageIndices.isNotEmpty()) {
                val maxIndex = messageIndices.maxOrNull() ?: continue
                lastReadMessageIndex[Pair(agentId, thread.id)] = maxIndex + 1
            }
        }
    }
}
