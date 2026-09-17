package com.ih.osm.features.auth.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.domain.model.AuthenticatedUser

interface AuthRepository {
    suspend fun login(email: String, password: String): NetworkResult<AuthenticatedUser>
    suspend fun logout()
}
