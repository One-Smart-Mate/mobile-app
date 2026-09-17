package com.ih.osm.features.auth.domain.session

import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {
    val status: StateFlow<SessionStatus>

    suspend fun restore()
    suspend fun establish(user: AuthenticatedUser, token: String)
    suspend fun invalidate()
    suspend fun currentUser(): AuthenticatedUser?
}
