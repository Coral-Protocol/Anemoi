package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

data class GroupScheduler(
    val groups: List<Set<String>> = listOf(),
) {
    @OptIn(ExperimentalAtomicApi::class)
    private val agentGroupRegisteredCount = List(groups.size) { i -> Pair(i, AtomicInt(0)) }.toMap()
    private val agentGroupRequiredCount = groups.mapIndexed { i, grp -> Pair(i, grp.size) }.toMap()
    private val agentGroupMembership = groups.flatMapIndexed { i, grp -> grp.map { agent -> Pair(agent, i) } }.toMap()
    private val agentGroupNotifications = ConcurrentHashMap<Int, MutableList<CompletableDeferred<Boolean>>>()


    @OptIn(ExperimentalAtomicApi::class)
    fun clear() {
        agentGroupNotifications.clear()
        agentGroupRegisteredCount.values.forEach{it -> it.store(0)}
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun registerAgent(agentId: String) {
        // Increment the counter of agents registered for the agent's blocking group
        agentGroupMembership[agentId]?.let { group ->
            agentGroupRegisteredCount[group]?.let {
                val count = it.incrementAndFetch()
                val required = agentGroupRequiredCount[group]
                    ?: throw AssertionError("Group $group has registered counter, but no required count")

                if (count >= required) {
                    agentGroupNotifications[group]?.forEach { listener ->
                        if (!listener.isCompleted) listener.complete(
                            true
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    suspend fun waitForGroup(agentId: String, timeoutMs: Long): Boolean {
        // if there's no group for this agent, the agent must be non-blocking
        val group = agentGroupMembership[agentId] ?: return true
        val required = agentGroupRequiredCount[group]
            ?: throw AssertionError("Group $group implied through membership, but no required count exists")

        if ((agentGroupRegisteredCount[group]?.load() ?: 0) >= required) return true

        val deferred = CompletableDeferred<Boolean>()
        val deferredList = agentGroupNotifications.computeIfAbsent(group) { mutableListOf() }

        deferredList.add(deferred)

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: false

        if (!result) {
            // If the wait timed out, remove this deferred from the list
            agentGroupNotifications[group]?.let {
                it.remove(deferred)
                // If the list is now empty, remove the target count from the map
                if (it.isEmpty()) {
                    agentGroupNotifications.remove(group)
                }
            }
        }

        return result
    }
}