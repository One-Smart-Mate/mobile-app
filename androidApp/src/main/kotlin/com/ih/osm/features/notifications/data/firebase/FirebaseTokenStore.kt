package com.ih.osm.features.notifications.data.firebase

import android.content.Context

class FirebaseTokenStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun save(token: String) {
        preferences.edit().putString(TOKEN_KEY, token).apply()
    }

    fun token(): String? = preferences.getString(TOKEN_KEY, null)?.takeIf(String::isNotBlank)

    private companion object {
        const val PREFERENCES_NAME = "firebase_registration"
        const val TOKEN_KEY = "current_token"
    }
}
