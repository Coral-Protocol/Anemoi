package org.coralprotocol.coralserver.models

import kotlinx.serialization.Serializable
import java.util.*

/**
 * Represents a message in a thread.
 */
@Serializable
data class ResolvedMessage(
    val id: String,
    val threadName: String,
    val threadId: String,
    val senderId: String,
    val content: String,
    val timestamp: Long,
    val mentions: List<String>
)