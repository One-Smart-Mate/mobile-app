package com.ih.osm.core.auth

import eu.anifantakis.lib.ksafe.KSafe

interface TokenStorage {
    suspend fun getToken(): String?
    suspend fun saveToken(token: String)
    suspend fun clear()
}

internal class TokenStorageImpl(
    private val secureStorage: KSafe,
) : TokenStorage {
    override suspend fun getToken(): String? = secureStorage.get(TOKEN_KEY, null)

    override suspend fun saveToken(token: String) {
        secureStorage.put(TOKEN_KEY, token)
    }

    override suspend fun clear() {
        secureStorage.delete(TOKEN_KEY)
    }

    private companion object {
        const val TOKEN_KEY = "osm_auth_token"
    }
}
