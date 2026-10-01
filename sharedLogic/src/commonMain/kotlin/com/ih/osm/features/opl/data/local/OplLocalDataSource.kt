package com.ih.osm.features.opl.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ih.osm.database.AppDatabase
import com.ih.osm.database.OplOfflineRecord
import com.ih.osm.features.opl.domain.model.Opl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal data class OplLocalScope(val ownerId: Long, val apiBase: String, val siteId: Long)

internal class OplLocalDataSource(private val database: AppDatabase) {
    private val queries get() = database.oplOfflineQueries
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun record(scope: OplLocalScope, id: Long): OplOfflineRecord? =
        queries.selectLesson(scope.ownerId, scope.apiBase, scope.siteId, id).executeAsOneOrNull()

    fun decode(record: OplOfflineRecord): Opl {
        val opl = json.decodeFromString<Opl>(record.payload)
        if (opl.id != record.opl_id || opl.siteId != record.site_id ||
            opl.content.any { it.oplId != opl.id || it.siteId != opl.siteId }) {
            throw SerializationException("Offline OPL data does not match its lesson/site")
        }
        return opl.copy(isDownloaded = true, downloadRevision = record.generation)
    }

    fun lessons(scope: OplLocalScope): List<Opl> =
        queries.selectDownloaded(scope.ownerId, scope.apiBase, scope.siteId).executeAsList().map(::decode)

    fun observeIds(scope: OplLocalScope): Flow<Set<Long>> =
        queries.selectDownloaded(scope.ownerId, scope.apiBase, scope.siteId).asFlow()
            .mapToList(Dispatchers.Default).map { rows -> rows.map { it.opl_id }.toSet() }

    fun begin(scope: OplLocalScope, opl: Opl, generation: String) = database.transaction {
        // Recover incomplete staging data left by process termination. The repository holds its mutation lock.
        queries.deletePendingChunks(scope.ownerId, scope.apiBase, scope.siteId, opl.id,
            scope.ownerId, scope.apiBase, scope.siteId, opl.id)
        queries.deletePendingLessons(scope.ownerId, scope.apiBase, scope.siteId, opl.id)
        queries.insertLesson(scope.ownerId, scope.apiBase, scope.siteId, opl.id, generation, json.encodeToString(opl))
    }

    fun append(scope: OplLocalScope, oplId: Long, generation: String, contentId: Long, index: Long, bytes: ByteArray) {
        queries.insertChunk(scope.ownerId, scope.apiBase, scope.siteId, oplId, generation, contentId, index, bytes)
    }

    fun chunks(scope: OplLocalScope, oplId: Long, generation: String, contentId: Long): Long =
        queries.countChunks(scope.ownerId, scope.apiBase, scope.siteId, oplId, generation, contentId).executeAsOne()

    fun chunk(scope: OplLocalScope, oplId: Long, generation: String, contentId: Long, index: Long): ByteArray =
        queries.selectChunk(scope.ownerId, scope.apiBase, scope.siteId, oplId, generation, contentId, index)
            .executeAsOne()

    fun commit(scope: OplLocalScope, oplId: Long, generation: String) = database.transaction {
        queries.deleteOtherChunks(scope.ownerId, scope.apiBase, scope.siteId, oplId, generation)
        queries.deleteOtherLessons(scope.ownerId, scope.apiBase, scope.siteId, oplId, generation)
        queries.markReady(scope.ownerId, scope.apiBase, scope.siteId, oplId, generation)
    }

    fun discard(scope: OplLocalScope, oplId: Long, generation: String) = database.transaction {
        queries.deleteGenerationChunks(scope.ownerId, scope.apiBase, scope.siteId, oplId, generation)
        queries.deleteGeneration(scope.ownerId, scope.apiBase, scope.siteId, oplId, generation)
    }

    fun delete(scope: OplLocalScope, oplId: Long) = database.transaction {
        queries.deleteLessonChunks(scope.ownerId, scope.apiBase, scope.siteId, oplId)
        queries.deleteLesson(scope.ownerId, scope.apiBase, scope.siteId, oplId)
    }
}
