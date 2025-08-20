//TODO: Determine whether we should allow connection via stdio
//package org.coralprotocol.coralserver.server
//
//import io.github.oshai.kotlinlogging.KotlinLogging
//import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.runBlocking
//import kotlinx.io.asSink
//import kotlinx.io.asSource
//import kotlinx.io.buffered
//
//private val logger = KotlinLogging.logger {}
//
///**
// * Runs an MCP server using standard input/output.
// * The server will handle listing prompts, tools, and resources automatically.
// */
//fun runMcpServerUsingStdio() {
//    // Note: The server will handle listing prompts, tools, and resources automatically.
//    // The handleListResourceTemplates will return empty as defined in the Server code.
//    val server = createCoralMcpServer()
//    val transport = StdioServerTransport(
//        inputStream = System.`in`.asSource().buffered(),
//        outputStream = System.out.asSink().buffered()
//    )
//
//    runBlocking {
//        server.connect(transport)
//        val done = Job()
//        done.join()
//        logger.info { "Server closed" }
//    }
//}