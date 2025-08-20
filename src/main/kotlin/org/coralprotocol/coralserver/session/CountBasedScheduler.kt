package org.coralprotocol.coralserver.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

class CountBasedScheduler() {
    private val agentCountNotifications = ConcurrentHashMap<Int, MutableList<CompletableDeferred<Boolean>>>()
    @OptIn(ExperimentalAtomicApi::class)
    private var registeredAgentsCount = AtomicInt(0)

    @OptIn(ExperimentalAtomicApi::class)
    fun getRegisteredAgentsCount(): Int = registeredAgentsCount.load()

    @OptIn(ExperimentalAtomicApi::class)
    fun clear() {
        agentCountNotifications.clear()
        registeredAgentsCount.store(0)
    }

    @OptIn(ExperimentalAtomicApi::class)
    fun registerAgent(agentId: String) {
        registeredAgentsCount.incrementAndFetch()

        // Create a copy of the keys to avoid ConcurrentModificationException
        val targetCounts = agentCountNotifications.keys.toList()

        // For each target count that has been reached
        for (targetCount in targetCounts) {
            if (registeredAgentsCount.load() >= targetCount) {
                // Get the list of deferreds for this target count
                val deferredList = agentCountNotifications[targetCount]
                if (deferredList != null) {
                    // Complete all deferreds that are not already completed
                    for (deferred in deferredList) {
                        if (!deferred.isCompleted) {
                            deferred.complete(true)
                        }
                    }
                    // Remove this target count from the map
                    agentCountNotifications.remove(targetCount)
                }
            }
        }
    }


    @OptIn(ExperimentalAtomicApi::class)
    suspend fun waitForAgentCount(targetCount: Int, timeoutMs: Long): Boolean {
        if (registeredAgentsCount.load() >= targetCount) return true

        val deferred = CompletableDeferred<Boolean>()

        val deferredList = agentCountNotifications.computeIfAbsent(targetCount) { mutableListOf() }
        deferredList.add(deferred)

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: false

        if (!result) {
            // If the wait timed out, remove this deferred from the list
            val deferredsList = agentCountNotifications[targetCount]
            if (deferredsList != null) {
                deferredsList.remove(deferred)
                // If the list is now empty, remove the target count from the map
                if (deferredsList.isEmpty()) {
                    agentCountNotifications.remove(targetCount)
                }
            }
        }

        return result
    }
}