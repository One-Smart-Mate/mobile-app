package com.ih.osm.features.auth

import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.auth.domain.session.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class IosSessionController(
    private val sessionRepository: SessionRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observationJob: Job? = null

    fun start(onStatusChanged: (SessionStatus) -> Unit) {
        observationJob?.cancel()
        observationJob = scope.launch {
            sessionRepository.status.collectLatest(onStatusChanged)
        }
    }

    suspend fun restore() {
        sessionRepository.restore()
    }

    fun stop() {
        observationJob?.cancel()
        observationJob = null
    }
}
