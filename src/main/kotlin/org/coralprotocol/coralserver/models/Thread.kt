package org.coralprotocol.coralserver.models

import kotlinx.serialization.Serializable
import java.util.*

/**
 * Represents a thread with participants.
 */
data class Thread(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val creatorId: String,
    val participants: MutableList<String> = mutableListOf(),
    val messages: MutableList<Message> = mutableListOf(),
    var isClosed: Boolean = false,
    var summary: String? = null
)

@Serializable
data class ResolvedThread(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val creatorId: String,
    val participants: List<String> = listOf(),
    val messages: List<ResolvedMessage> = listOf(),
    var isClosed: Boolean = false,
    var summary: String? = null
)

fun Thread.resolve(): ResolvedThread = ResolvedThread(
    id = id,
    name = name,
    creatorId = creatorId,
    participants = participants,
    messages = messages.map { it.resolve() },
    isClosed = isClosed,
    summary = summary
)