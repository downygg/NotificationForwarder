package com.itsazni.notificationforwarder.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ListenerHealthState {
    UNKNOWN,
    CONNECTED,
    DISCONNECTED
}

internal class ListenerHealthTracker(
    initialState: ListenerHealthState = ListenerHealthState.UNKNOWN
) {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<ListenerHealthState> = mutableState.asStateFlow()

    fun markConnected() {
        mutableState.value = ListenerHealthState.CONNECTED
    }

    fun markDisconnected(
        requestRebind: () -> Unit,
        onFailure: (Throwable) -> Unit = {}
    ) {
        mutableState.value = ListenerHealthState.DISCONNECTED
        runCatching(requestRebind).onFailure(onFailure)
    }
}

object ListenerHealth {
    private val tracker = ListenerHealthTracker()
    val state: StateFlow<ListenerHealthState> = tracker.state

    fun markConnected() {
        tracker.markConnected()
    }

    fun markDisconnected(
        requestRebind: () -> Unit,
        onFailure: (Throwable) -> Unit = {}
    ) {
        tracker.markDisconnected(requestRebind, onFailure)
    }
}
