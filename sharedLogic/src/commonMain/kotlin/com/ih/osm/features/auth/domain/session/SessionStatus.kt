package com.ih.osm.features.auth.domain.session

import com.ih.osm.features.auth.domain.model.AuthenticatedUser

sealed interface SessionStatus {
    data object Unknown : SessionStatus
    data object Unauthenticated : SessionStatus
    data class Authenticated(val user: AuthenticatedUser) : SessionStatus
}
