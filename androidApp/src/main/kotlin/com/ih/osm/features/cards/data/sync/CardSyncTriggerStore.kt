package com.ih.osm.features.cards.data.sync

import android.content.Context

/**
 * Persists remote sites that need a delta pull. A generation prevents a trigger
 * received during an active pull from being accidentally acknowledged.
 */
class CardSyncTriggerStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun add(siteId: Long) {
        if (siteId <= 0) return
        val nextGeneration = preferences.getLong(generationKey(siteId), 0L) + 1L
        preferences.edit()
            .putLong(generationKey(siteId), nextGeneration)
            .putBoolean(pendingKey(siteId), true)
            .commit()
    }

    @Synchronized
    fun addAll(siteIds: Collection<Long>) {
        siteIds.filter { it > 0 }.distinct().forEach(::add)
    }

    @Synchronized
    fun snapshot(): Map<Long, Long> = preferences.all.keys
        .asSequence()
        .filter { it.startsWith(PENDING_PREFIX) && preferences.getBoolean(it, false) }
        .mapNotNull { key ->
            key.removePrefix(PENDING_PREFIX).toLongOrNull()?.let { siteId ->
                siteId to preferences.getLong(generationKey(siteId), 0L)
            }
        }
        .toMap()

    @Synchronized
    fun acknowledge(siteId: Long, generation: Long) {
        if (preferences.getLong(generationKey(siteId), 0L) != generation) return
        preferences.edit().remove(pendingKey(siteId)).apply()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun pendingKey(siteId: Long) = "$PENDING_PREFIX$siteId"
    private fun generationKey(siteId: Long) = "$GENERATION_PREFIX$siteId"

    private companion object {
        const val PREFERENCES_NAME = "card_sync_triggers"
        const val PENDING_PREFIX = "pending_site_"
        const val GENERATION_PREFIX = "generation_site_"
    }
}
