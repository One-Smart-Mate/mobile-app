package com.ih.osm.features.auth.data.repository

import com.ih.osm.core.auth.TokenStorage
import com.ih.osm.features.auth.data.local.AuthLocalDataSource
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.auth.domain.session.SessionStatus
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class SessionRepositoryImpl(
    private val tokenStorage: TokenStorage,
    private val localDataSource: AuthLocalDataSource,
) : SessionRepository {
    private val mutableStatus = MutableStateFlow<SessionStatus>(SessionStatus.Unknown)
    override val status: StateFlow<SessionStatus> = mutableStatus.asStateFlow()
    private val mutationMutex = Mutex()

    override suspend fun restore() {
        mutationMutex.withLock {
            try {
                val token = tokenStorage.getToken()
                val user = localDataSource.getUser()
                mutableStatus.value = if (!token.isNullOrBlank() && user != null) {
                    SessionStatus.Authenticated(user)
                } else {
                    clearPersistedSession()
                    SessionStatus.Unauthenticated
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                clearPersistedSession()
                mutableStatus.value = SessionStatus.Unauthenticated
            }
        }
    }

    override suspend fun establish(user: AuthenticatedUser, token: String) {
        require(token.isNotBlank()) { "Authentication token cannot be blank" }
        mutationMutex.withLock {
            try {
                tokenStorage.saveToken(token)
                localDataSource.saveUser(user)
                mutableStatus.value = SessionStatus.Authenticated(user)
            } catch (error: Throwable) {
                clearPersistedSession()
                mutableStatus.value = SessionStatus.Unauthenticated
                throw error
            }
        }
    }

    override suspend fun invalidate() {
        mutationMutex.withLock {
            clearPersistedSession()
            mutableStatus.value = SessionStatus.Unauthenticated
        }
    }

    override suspend fun currentUser(): AuthenticatedUser? {
        if (status.value is SessionStatus.Unknown) restore()
        return (status.value as? SessionStatus.Authenticated)?.user
    }

    private suspend fun clearPersistedSession() {
        withContext(NonCancellable) {
            runCatching { tokenStorage.clear() }
            runCatching { localDataSource.deleteUser() }
        }
    }
}
