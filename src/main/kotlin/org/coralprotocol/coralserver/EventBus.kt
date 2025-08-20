package org.coralprotocol.coralserver

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class EventBus<E>(val replay: Int = 0) {
    private val _events = MutableSharedFlow<E>(extraBufferCapacity = 1024, replay = replay) // private mutable shared flow
    val events = _events.asSharedFlow() // publicly exposed as read-only shared flow

    fun emit(event: E) {
        _events.tryEmit(event) // suspends until all subscribers receive it
    }
}