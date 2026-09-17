package com.ih.osm.core.feature.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

abstract class GRViewModel<STATE : Any, ACTION : Any, EVENT : Any>(
    initialState: STATE,
) : ViewModel(
    viewModelScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    ),
) {
    private val uiState = MutableStateFlow(initialState)

    fun getStateFlow(): StateFlow<STATE> = uiState.asStateFlow()

    fun getStateValue(): STATE = getStateFlow().value

    private val eventFlow = Channel<EVENT>()

    fun getEventFlow() = eventFlow.receiveAsFlow()

    protected fun setState(updater: STATE.() -> STATE) {
        uiState.update { currentState -> updater.invoke(currentState) }
    }

    fun process(action: ACTION) {
        processImpl(action)
    }

    protected suspend fun sendNewEvent(event: EVENT) {
        eventFlow.send(event)
    }

    protected abstract fun processImpl(action: ACTION)
}
