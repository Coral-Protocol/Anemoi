package org.coralprotocol.coralserver.e2e

import kotlinx.coroutines.*
import org.eclipse.lmos.arc.agents.agent.ask
import org.eclipse.lmos.arc.core.Success
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test


class E2EResourceTest {
    val port: UShort = 14391u
    var server = TestCoralServer(port = port, devmode = true)

    @OptIn(DelicateCoroutinesApi::class)
    @BeforeEach
    fun setup() {
        server.setup()
    }
//
//    @Test
//    fun testCreateThreadAndPostMessage(): Unit = runBlocking {
//        var allAssertsCompleted = false
//        createSessionWithConnectedAgents(server.server!!, sessionId =  "test", privacyKey = "aaa", applicationId = "aaa", noAgentsOptional = true) {
//            val agent1 = agent("testAgent1", "testAgent1")
//            val agent2 = agent("testAgent2", "testAgent2")
//
//            onAgentsCreated = {
//                agent1.getConnected().ask("Say hello to testAgent2 in a new thread. Tell it the passcode 3243")
//                val sessions = server.sessionManager.getAllSessions()
//                assert(sessions.size == 1) { "There should be one session" }
//                val session = sessions.first()
//                val threads = session.getAllThreads()
//                assert(threads.size == 1) { "There should be one thread" }
//                val thread = threads.first()
//                val messages = thread.messages
//                assert(messages.size == 1) { "There should be one message" }
//
//                // Verify agent2 can receive the message
//                val agent2Response = agent2.getConnected().ask("What is the passcode testAgent1 just told you? use wait for mentions to check") as Success<String>
//                assert(agent2Response.value.contains("3243")) { "Agent2 should receive the code from agent1" }
//
//                // Verify agent2 can send back a message in the same thread
//                val agent2Response2 = agent2.getConnected().ask("The passcode is 9920. Pass it to testAgent1 in the same thread") as Success<String>
//                val agent1PasscodeFrom2Resp = agent1.getConnected().ask("What is the passcode? testAgent2 just told you? use wait for mentions to check") as Success<String>
//
//                assert(agent1PasscodeFrom2Resp.value.contains("9920")) { "Agent1 should receive the right code returning from agent 2" }
//                assert(session.getAllThreads().size == 1) { "There should still be one thread" }
//                allAssertsCompleted = true
//            }
//        }
//        assert(allAssertsCompleted) { "All asserts completed." }
//    }
}
