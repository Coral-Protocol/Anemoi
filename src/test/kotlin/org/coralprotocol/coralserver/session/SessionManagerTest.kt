package org.coralprotocol.coralserver.session

import kotlinx.coroutines.runBlocking
import org.coralprotocol.coralserver.models.Agent
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class SessionManagerTest {

    var sessionManager = SessionManager(port = 0u)
    @BeforeEach
    fun setup() {
        sessionManager = SessionManager(port = 0u)
    }

    @Test
    fun `test create session with random ID`() {
        // Create a session with a random ID
        val session = sessionManager.createSession("app1", "key1")

        // Verify session was created with correct properties
        assertNotNull(session)
        assertEquals("app1", session.applicationId)
        assertEquals("key1", session.privacyKey)
        assertTrue(session.id.isNotEmpty())

        // Verify session can be retrieved
        val retrievedSession = sessionManager.getSession(session.id)
        assertEquals(session, retrievedSession)
    }

    @Test
    fun `test create session with specific ID`() {
        // Create a session with a specific ID
        val session = sessionManager.createSessionWithId("session1", "app1", "key1")

        // Verify session was created with correct properties
        assertNotNull(session)
        assertEquals("session1", session.id)
        assertEquals("app1", session.applicationId)
        assertEquals("key1", session.privacyKey)

        // Verify session can be retrieved
        val retrievedSession = sessionManager.getSession("session1")
        assertEquals(session, retrievedSession)
    }

    @Test
    fun `test get or create session - existing session`() = runBlocking {
        // Create a session first
        sessionManager.createSessionWithId("session2", "app1", "key1")

        // Get the existing session
        val session = sessionManager.getOrCreateSession("session2", "app2", "key2")

        // Verify the existing session is returned (not a new one with updated properties)
        assertEquals("session2", session.id)
        assertEquals("app1", session.applicationId) // Should still be app1, not app2
        assertEquals("key1", session.privacyKey) // Should still be key1, not key2
    }

    @Test
    fun `test get or create session - new session`() = runBlocking {
        // Get or create a new session
        val session = sessionManager.getOrCreateSession("session3", "app3", "key3")

        // Verify a new session was created
        assertEquals("session3", session.id)
        assertEquals("app3", session.applicationId)
        assertEquals("key3", session.privacyKey)

        // Verify session can be retrieved
        val retrievedSession = sessionManager.getSession("session3")
        assertEquals(session, retrievedSession)
    }

    @Test
    fun `test get session - non-existent session`() {
        // Try to get a non-existent session
        val session = sessionManager.getSession("nonexistent")

        // Verify null is returned
        assertNull(session)
    }

    @Test
    fun `test get all sessions`() {
        // Create multiple sessions
        val session1 = sessionManager.createSessionWithId("session1", "app1", "key1")
        val session2 = sessionManager.createSessionWithId("session2", "app2", "key2")
        val session3 = sessionManager.createSessionWithId("session3", "app3", "key3")

        // Get all sessions
        val sessions = sessionManager.getAllSessions()

        // Verify all sessions are returned
        assertEquals(3, sessions.size)
        assertTrue(sessions.contains(session1))
        assertTrue(sessions.contains(session2))
        assertTrue(sessions.contains(session3))
    }

    @Test
    fun `test threads are not available across sessions with different privacy keys`() {
        // Create two sessions with different privacy keys
        val session1 = sessionManager.createSessionWithId("session1", "app1", "key1")
        val session2 = sessionManager.createSessionWithId("session2", "app1", "key2")

        // Register agents in both sessions
        val creator1 = session1.registerAgent(agentId = "creator1") ?: throw AssertionError("could not register agent")
        val participant1 = session1.registerAgent(agentId = "participant1") ?: throw AssertionError("could not register agent")

        val creator2 = session2.registerAgent(agentId = "creator2") ?: throw AssertionError("could not register agent")
        val participant2 = session2.registerAgent(agentId = "participant2") ?: throw AssertionError("could not register agent")

        // Create a thread in the first session
        val thread1 = session1.createThread(
            name = "Thread in Session 1",
            creatorId = creator1.id,
            participantIds = listOf("participant1")
        )

        // Create a thread in the second session
        val thread2 = session2.createThread(
            name = "Thread in Session 2",
            creatorId = creator2.id,
            participantIds = listOf("participant2")
        )

        // Verify threads were created
        assertNotNull(thread1)
        assertNotNull(thread2)

        // Verify thread1 is accessible in session1
        val retrievedThread1 = session1.getThread(thread1.id)
        assertNotNull(retrievedThread1)
        assertEquals("Thread in Session 1", retrievedThread1?.name)

        // Verify thread2 is accessible in session2
        val retrievedThread2 = session2.getThread(thread2.id)
        assertNotNull(retrievedThread2)
        assertEquals("Thread in Session 2", retrievedThread2?.name)

        // Verify thread1 is NOT accessible in session2
        val thread1InSession2 = session2.getThread(thread1.id)
        assertNull(thread1InSession2)

        // Verify thread2 is NOT accessible in session1
        val thread2InSession1 = session1.getThread(thread2.id)
        assertNull(thread2InSession1)
    }

    @Test
    fun `test agents are not available across sessions with different privacy keys`() {
        // Create two sessions with different privacy keys
        val session1 = sessionManager.createSessionWithId("session1", "app1", "key1")
        val session2 = sessionManager.createSessionWithId("session2", "app1", "key2")

        // Register agents in both sessions with the same IDs
        val agent1 = session1.registerAgent(agentId = "agent1") ?: throw AssertionError("could not register agent")
        val agent2 = session1.registerAgent(agentId = "agent2") ?: throw AssertionError("could not register agent")

        val agent1InSession2 = session2.registerAgent(agentId = "agent1") ?: throw AssertionError("could not register agent")
        val agent2InSession2 = session2.registerAgent(agentId = "agent2") ?: throw AssertionError("could not register agent")

        // Verify agents were registered in their respective sessions
        val retrievedAgent1InSession1 = session1.getAgent(agent1.id)
        assertNotNull(retrievedAgent1InSession1)

        val retrievedAgent1InSession2 = session2.getAgent(agent1InSession2.id)
        assertNotNull(retrievedAgent1InSession2)

        // Verify agents in different sessions with the same ID are different objects
        assertNotEquals(retrievedAgent1InSession1, retrievedAgent1InSession2)

        // Verify the count of agents in each session
        assertEquals(2, session1.getAllAgents().size)
        assertEquals(2, session2.getAllAgents().size)

        // Verify that creating a thread with agents from another session fails
        val thread1 = session1.createThread(
            name = "Thread in Session 1",
            creatorId = "agent1",
            participantIds = listOf("agent2")
        )

        assertNotNull(thread1)

        // Try to create a thread in session2 with agent1 from session1 as a participant
        // This should still create the thread but only with valid participants from session2
        val thread2 = session2.createThread(
            name = "Thread in Session 2",
            creatorId = "agent1",
            participantIds = listOf("nonexistent")
        )

        assertNotNull(thread2)
        assertEquals(1, thread2.participants.size) // Only the creator should be included
    }
}
