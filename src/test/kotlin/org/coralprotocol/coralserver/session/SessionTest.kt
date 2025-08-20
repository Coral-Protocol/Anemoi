package org.coralprotocol.coralserver.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.models.Agent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicBoolean

class SessionTest {
    private lateinit var session: CoralAgentGraphSession

    @BeforeEach
    fun setup() {
        // Create a new session for each test
        session = CoralAgentGraphSession("test-session", "test-app", "test-key", agentGraph = null)
        // Clear any existing data
        session.clearAll()
    }

    @Test
    fun `test agent registration`() {
        // Register a new agent
        val agent = session.registerAgent("agent1")

        // Verify agent was registered
        assertNotNull(agent)
        assertEquals(agent, session.getAgent(agent!!.id))

        // Try to register the same agent again
        val duplicateAgent = session.registerAgent("agent1")
        assertNull(duplicateAgent)
    }

//    @Test
//    fun `test agent registration with description`() {
//        // Register a new agent with description
//        val agent = session.registerAgent("agent2", "This agent is responsible for testing")
//
//        // Verify agent was registered with description
//        assertNotNull(agent)
//        val retrievedAgent = session.getAgent(agent!!.id)
//        assertEquals(agent, retrievedAgent)
//        assertEquals("This agent is responsible for testing", retrievedAgent?.description)
//    }

    @Test
    fun `test thread creation`() {
        // Register agents
        val creator = session.registerAgent("creator")!!
        val participant1 = session.registerAgent("participant1")!!
        val participant2 = session.registerAgent("participant2")!!

        // Create a thread
        val thread = session.createThread(
            name = "Test Thread",
            creatorId = creator.id,
            participantIds = listOf(participant1.id, participant2.id),
        )

        // Verify thread was created
        assertNotNull(thread)
        assertEquals("Test Thread", thread.name)
        assertEquals("creator", thread.creatorId)
        assertTrue(thread.participants.contains("creator"))
        assertTrue(thread.participants.contains("participant1"))
        assertTrue(thread.participants.contains("participant2"))
        assertEquals(3, thread.participants.size)
    }

    @Test
    fun `test adding and removing participants`() {
        // Register agents
        val creator = session.registerAgent("creator")
        val participant1 = session.registerAgent("participant1")
        val participant2 = session.registerAgent("participant2")
        val participant3 = session.registerAgent("participant3")

        // Create a thread
        val thread = session.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant1")
        )

        // Add a participant
        val addSuccess = session.addParticipantToThread(
            threadId = thread.id ?: "",
            participantId = "participant2"
        )

        // Verify participant was added
        assertTrue(addSuccess)
        val updatedThread = session.getThread(thread.id ?: "")
        assertTrue(updatedThread?.participants?.contains("participant2") ?: false)

        // Remove a participant
        val removeSuccess = session.removeParticipantFromThread(
            threadId = thread.id ?: "",
            participantId = "participant1"
        )

        // Verify participant was removed
        assertTrue(removeSuccess)
        val finalThread = session.getThread(thread.id ?: "")
        assertFalse(finalThread?.participants?.contains("participant1") ?: true)
    }

    @Test
    fun `test sending messages and closing thread`() {
        // Register agents
        val creator = session.registerAgent("creator")
        val participant = session.registerAgent("participant")

        // Create a thread
        val thread = session.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant")
        )

        // Send a message
        val message = session.sendMessage(
            threadId = thread.id ?: "",
            senderId = "creator",
            content = "Hello, world!",
            mentions = listOf("participant")
        )

        // Verify message was sent
        assertNotNull(message)
        assertEquals("Hello, world!", message.content)
        assertEquals("creator", message.sender.id)
        assertEquals(thread.id, message.thread.id)
        assertTrue(message.mentions.contains("participant") ?: false)

        // Close the thread
        val closeSuccess = session.closeThread(
            threadId = thread.id ?: "",
            summary = "Thread completed"
        )

        // Verify thread was closed
        assertTrue(closeSuccess)
        val closedThread = session.getThread(thread.id ?: "")
        assertTrue(closedThread?.isClosed ?: false)
        assertEquals("Thread completed", closedThread?.summary)

        // Try to send a message to a closed thread
        assertThrows<IllegalArgumentException> {
            val failedMessage = session.sendMessage(
                threadId = thread.id ?: "",
                senderId = "creator",
                content = "This should fail",
                mentions = listOf()
            )
        }
    }

    @Test
    fun `test waiting for mentions`() = runBlocking {
        // Register agents
        val creator = session.registerAgent("creator")
        val participant = session.registerAgent("participant")

        // Create a thread
        val thread = session.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant")
        )

        // Launch a coroutine to wait for mentions
        val waitJob = launch(Dispatchers.Default) {
            val messages = session.waitForMentions(
                agentId = "participant",
                timeoutMs = 5000
            )

            // Verify messages were received
            assertFalse(messages.isEmpty())
            assertEquals(1, messages.size)
            assertEquals("Hello, participant!", messages[0].content)
        }

        // Wait a bit to ensure the wait operation has started
        delay(100)

        // Send a message with a mention
        session.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "creator",
            content = "Hello, participant!",
            mentions = listOf("participant")
        )

        // Wait for the job to complete
        waitJob.join()
    }

    @Test
    fun `test waiting for mentions with timeout`() = runBlocking {
        // Register an agent
        val agent = session.registerAgent("agent")

        // Wait for mentions with a short timeout
        val messages = session.waitForMentions(
            agentId = "agent",
            timeoutMs = 100
        )

        // Verify no messages were received
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `test waiting for agent messages`() = runBlocking {
        // Register agents
        val creator = session.registerAgent("creator")
        val participant = session.registerAgent("participant")
        val otherAgent = session.registerAgent("otherAgent")

        // Create a thread
        val thread = session.createThread(
            name = "Test Thread",
            creatorId = "creator",
            participantIds = listOf("participant", "otherAgent")
        )

        // Launch a coroutine to wait for messages from specific agents
        val waitJob = launch(Dispatchers.Default) {
            val messages = session.waitForAgentMessages(
                agentId = "participant",
                fromAgentIds = listOf("creator"),
                timeoutMs = 5000
            )

            // Verify messages were received
            assertFalse(messages.isEmpty())
            assertEquals(1, messages.size)
            assertEquals("Hello from creator!", messages[0].content)
            assertEquals("creator", messages[0].sender.id)
        }

        // Wait a bit to ensure the wait operation has started
        delay(100)

        // Send a message from the creator
        session.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "creator",
            content = "Hello from creator!",
            mentions = listOf()
        )

        // Wait for the job to complete
        waitJob.join()
    }

    @Test
    fun `test waiting for agent messages with timeout`() = runBlocking {
        // Register agents
        val agent1 = session.registerAgent("agent1")
        val agent2 = session.registerAgent("agent2")

        // Wait for messages from agent2 with a short timeout
        val messages = session.waitForAgentMessages(
            agentId = "agent1",
            fromAgentIds = listOf("agent2"),
            timeoutMs = 100
        )

        // Verify no messages were received
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `test waiting for messages from multiple agents`() = runBlocking {
        // Register agents
        val agent1 = session.registerAgent("agent1")
        val agent2 = session.registerAgent("agent2")
        val agent3 = session.registerAgent("agent3")

        // Create a thread
        val thread = session.createThread(
            name = "Test Thread",
            creatorId = "agent1",
            participantIds = listOf("agent2", "agent3")
        )

        // Launch a coroutine to wait for messages from specific agents
        val waitJob = launch(Dispatchers.Default) {
            val messages = session.waitForAgentMessages(
                agentId = "agent1",
                fromAgentIds = listOf("agent2", "agent3"),
                timeoutMs = 5000
            )

            // Verify messages were received
            assertFalse(messages.isEmpty())
            assertEquals(1, messages.size)
            assertEquals("Hello from agent3!", messages[0].content)
            assertEquals("agent3", messages[0].sender.id)
        }

        // Wait a bit to ensure the wait operation has started
        delay(100)

        // Send a message from agent3
        session.sendMessage(
            threadId = thread?.id ?: "",
            senderId = "agent3",
            content = "Hello from agent3!",
            mentions = listOf()
        )

        // Wait for the job to complete
        waitJob.join()
    }

    @Test
    fun `test listing all agents`() {
        // Register multiple agents
        val agent1 = session.registerAgent("agent1")
        val agent2 = session.registerAgent("agent2")
        val agent3 = session.registerAgent("agent3")

        // Get all agents
        val agents = session.getAllAgents()

        // Verify all agents are returned
        assertEquals(3, agents.size)
        assertTrue(agents.contains(agent1))
        assertTrue(agents.contains(agent2))
        assertTrue(agents.contains(agent3))
    }

    @Test
    fun `test waiting for agent count`() = runBlocking {
        // Register some agents
        val agent1 = session.registerAgent("agent1")
        val agent2 = session.registerAgent("agent2")

        // Verify current count
        assertEquals(2, session.getRegisteredAgentsCount())

        // Launch a coroutine to wait for more agents
        val waitJob = launch(Dispatchers.Default) {
            val result = session.waitForAgentCount(
                targetCount = 3,
                timeoutMs = 5000
            )

            // Verify wait was successful
            assertTrue(result)
            assertEquals(3, session.getRegisteredAgentsCount())
        }

        // Wait a bit to ensure the wait operation has started
        delay(100)

        // Register another agent
        val agent3 = session.registerAgent("agent3")

        // Wait for the job to complete
        waitJob.join()
    }

    @Test
    fun `test waiting for agent count with timeout`() = runBlocking {
        // Register some agents
        val agent1 = session.registerAgent("agent1")

        // Wait for more agents with a short timeout
        val result = session.waitForAgentCount(
            targetCount = 3,
            timeoutMs = 100
        )

        // Verify wait timed out
        assertFalse(result)
        assertEquals(1, session.getRegisteredAgentsCount())
    }

    @Test
    fun `test get threads for agent`() {
        // Register agents
        val creator = session.registerAgent("creator")
        val participant1 = session.registerAgent("participant1")
        val participant2 = session.registerAgent("participant2")

        // Create threads
        val thread1 = session.createThread(
            name = "Thread 1",
            creatorId = "creator",
            participantIds = listOf("participant1")
        )

        val thread2 = session.createThread(
            name = "Thread 2",
            creatorId = "creator",
            participantIds = listOf("participant1", "participant2")
        )

        val thread3 = session.createThread(
            name = "Thread 3",
            creatorId = "participant2",
            participantIds = listOf("creator")
        )

        // Get threads for participant1
        val threadsForParticipant1 = session.getThreadsForAgent("participant1")

        // Verify correct threads are returned
        assertEquals(2, threadsForParticipant1.size)
        assertTrue(threadsForParticipant1.contains(thread1))
        assertTrue(threadsForParticipant1.contains(thread2))
        assertFalse(threadsForParticipant1.contains(thread3))

        // Get threads for participant2
        val threadsForParticipant2 = session.getThreadsForAgent("participant2")

        // Verify correct threads are returned
        assertEquals(2, threadsForParticipant2.size)
        assertTrue(threadsForParticipant2.contains(thread2))
        assertTrue(threadsForParticipant2.contains(thread3))
        assertFalse(threadsForParticipant2.contains(thread1))
    }

    @Test
    fun `test multiple connections from same client with waitForAgents`() = runBlocking {
        // Set the required agent count
        session.devRequiredAgentStartCount = 3

        // Create flags to track when each agent is registered
        val agent1Registered = AtomicBoolean(false)
        val agent2Registered = AtomicBoolean(false)
        val agent3Registered = AtomicBoolean(false)

        // Launch 3 coroutines to simulate 3 concurrent connections
        val connectionJobs = List(3) { index ->
            launch(Dispatchers.IO) {
                // Simulate a delay between connections
                delay(100L * index)

                // Create an agent for this connection
                val agentId = "agent-${index + 1}"
                val agent = session.registerAgent(agentId)

                // Set the flag for this agent
                when (index) {
                    0 -> agent1Registered.set(true)
                    1 -> agent2Registered.set(true)
                    2 -> agent3Registered.set(true)
                }

                // If this is the first or second agent, wait for all agents to be registered
                if (index < 2) {
                    println("[DEBUG_LOG] Agent $agentId waiting for all agents to be registered")
                    val result = session.waitForAgentCount(
                        targetCount = 3,
                        timeoutMs = 5000
                    )
                    println("[DEBUG_LOG] Agent $agentId wait result: $result")

                    // Verify wait was successful
                    assertTrue(result, "Agent $agentId wait should succeed")
                    assertEquals(3, session.getRegisteredAgentsCount(), "All 3 agents should be registered")

                    // Verify all agents are registered
                    assertTrue(agent1Registered.get(), "Agent 1 should be registered")
                    assertTrue(agent2Registered.get(), "Agent 2 should be registered")
                    assertTrue(agent3Registered.get(), "Agent 3 should be registered")
                }
            }
        }

        // Wait for all connections to complete
        connectionJobs.forEach { it.join() }

        // Verify that all 3 agents are registered
        assertEquals(3, session.getRegisteredAgentsCount(), "All 3 agents should be registered")

        // Verify that all 3 agents are in the session
        val agents = session.getAllAgents()
        assertEquals(3, agents.size, "Session should have 3 registered agents")
        assertTrue(agents.any { it.id == "agent-1" }, "Agent 1 should be registered")
        assertTrue(agents.any { it.id == "agent-2" }, "Agent 2 should be registered")
        assertTrue(agents.any { it.id == "agent-3" }, "Agent 3 should be registered")
    }
}
