package com.ih.osm.features.opl.data.repository

import com.ih.osm.core.network.NetworkError
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.auth.domain.session.SessionRepository
import com.ih.osm.features.level.domain.repository.LevelRepository
import com.ih.osm.features.opl.data.remote.OplApiService
import com.ih.osm.features.opl.data.remote.OplDto
import com.ih.osm.features.opl.domain.model.Opl
import com.ih.osm.features.opl.domain.repository.OplRepository
import kotlinx.serialization.SerializationException

internal class OplRepositoryImpl(
    private val api: OplApiService,
    private val sessionRepository: SessionRepository,
    private val levelRepository: LevelRepository,
) : OplRepository {
    override suspend fun getBySite(siteId: Long): NetworkResult<List<Opl>> =
        withSiteAccess(siteId) { api.getBySite(siteId).toDomainList(siteId) }

    override suspend fun search(siteId: Long, query: String): NetworkResult<List<Opl>> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return getBySite(siteId)
        if (normalizedQuery.length > MAXIMUM_QUERY_LENGTH) {
            return clientFailure("OPL search cannot exceed $MAXIMUM_QUERY_LENGTH characters.", 400)
        }
        return withSiteAccess(siteId) { api.search(siteId, normalizedQuery).toDomainList(siteId) }
    }

    override suspend fun getByLevel(siteId: Long, levelId: String): NetworkResult<List<Opl>> =
        withSiteAccess(siteId) {
            val numericLevelId = levelId.trim().toLongOrNull()?.takeIf { it > 0 }
                ?: return@withSiteAccess clientFailure("Invalid LEVEL identifier.", 400)
            val belongsToSite = levelRepository.getAll(siteId).any {
                it.id.toLongOrNull() == numericLevelId
            }
            if (!belongsToSite) {
                return@withSiteAccess clientFailure(
                    "The LEVEL is not in this site's synchronized catalog.",
                    400,
                )
            }
            api.getByLevel(numericLevelId).toDomainList(siteId)
        }

    override suspend fun getById(siteId: Long, oplId: Long): NetworkResult<Opl> =
        withSiteAccess(siteId) {
            if (oplId <= 0) return@withSiteAccess clientFailure("Invalid OPL identifier.", 400)
            api.getById(oplId).mapData { dto ->
                val opl = dto.toDomain()
                if (opl.siteId != siteId || opl.id != oplId) {
                    throw SerializationException("OPL response does not match the requested lesson/site.")
                }
                opl
            }
        }

    private suspend fun <T> withSiteAccess(
        siteId: Long,
        read: suspend () -> NetworkResult<T>,
    ): NetworkResult<T> {
        if (siteId <= 0) return clientFailure("Invalid site identifier.", 400)
        val user = sessionRepository.currentUser()
            ?: return clientFailure("User not authenticated.", 401)
        if (user.sites.none { it.id == siteId }) {
            return clientFailure("Site access denied.", 403)
        }

        val result = read()
        if (result is NetworkResult.Failure) return result
        // Do not deliver an in-flight read to a different or expired session.
        val currentUser = sessionRepository.currentUser()
        if (currentUser?.id != user.id) return clientFailure("Session changed during OPL request.", 401)
        if (currentUser.sites.none { it.id == siteId }) {
            return clientFailure("Site access denied.", 403)
        }
        return result
    }

    private fun NetworkResult<List<OplDto>>.toDomainList(siteId: Long): NetworkResult<List<Opl>> =
        mapData { items ->
            items.map { it.toDomain() }.also { opls ->
                if (opls.any { it.siteId != siteId }) {
                    throw SerializationException("OPL response contains lessons from another site.")
                }
            }.distinctBy { it.id }
                .sortedWith(compareBy<Opl> { it.order }.thenBy { it.id })
        }

    private inline fun <T, R> NetworkResult<T>.mapData(mapper: (T) -> R): NetworkResult<R> =
        when (this) {
            is NetworkResult.Failure -> this
            is NetworkResult.Success -> try {
                NetworkResult.Success(mapper(data), statusCode)
            } catch (error: SerializationException) {
                NetworkResult.Failure(
                    NetworkError(
                        kind = NetworkErrorKind.SERIALIZATION,
                        message = error.message ?: "Invalid OPL server response.",
                        statusCode = statusCode,
                    ),
                )
            }
        }

    private fun clientFailure(message: String, statusCode: Int) = NetworkResult.Failure(
        NetworkError(NetworkErrorKind.CLIENT, message, statusCode),
    )

    private companion object {
        const val MAXIMUM_QUERY_LENGTH = 100
    }
}
