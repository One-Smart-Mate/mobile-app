package com.ih.osm.features.auth.data.local

import com.ih.osm.features.auth.domain.model.AuthenticatedUser

internal interface AuthLocalDataSource {
    suspend fun saveUser(user: AuthenticatedUser)
    suspend fun getUser(): AuthenticatedUser?
    suspend fun deleteUser()
}
