package com.ih.osm.features.settings.domain.manager

import com.ih.osm.features.auth.domain.model.AuthenticatedUser

interface LogoutManager {
    suspend fun logout(user: AuthenticatedUser)
}
