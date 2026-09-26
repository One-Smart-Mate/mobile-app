package com.ih.osm.features.catalog.domain.usecase

import com.ih.osm.core.network.NetworkError
import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.cardtype.domain.repository.CardTypeRepository
import com.ih.osm.features.catalog.domain.model.CatalogKind
import com.ih.osm.features.catalog.domain.model.CatalogSyncPlan
import com.ih.osm.features.catalog.domain.model.CatalogSyncProgress
import com.ih.osm.features.catalog.domain.model.SiteCatalogSyncRequest
import com.ih.osm.features.catalog.domain.model.SiteCatalogs
import com.ih.osm.features.catalog.domain.repository.CatalogSyncRepository
import com.ih.osm.features.employee.domain.repository.EmployeeRepository
import com.ih.osm.features.level.domain.repository.LevelRepository
import com.ih.osm.features.preclassifier.domain.repository.PreclassifierRepository
import com.ih.osm.features.priority.domain.repository.PriorityRepository
import kotlin.time.Clock

sealed interface CatalogSyncResult {
    data object Success : CatalogSyncResult
    data class Failure(val error: NetworkError) : CatalogSyncResult
}

class SyncCatalogsUseCase(
    private val cardTypeRepository: CardTypeRepository,
    private val preclassifierRepository: PreclassifierRepository,
    private val priorityRepository: PriorityRepository,
    private val levelRepository: LevelRepository,
    private val employeeRepository: EmployeeRepository,
    private val catalogSyncRepository: CatalogSyncRepository,
) {
    suspend operator fun invoke(
        plan: CatalogSyncPlan,
        onProgress: suspend (CatalogSyncProgress) -> Unit = {},
    ): CatalogSyncResult {
        var completedSteps = 0
        val downloaded = mutableListOf<SiteCatalogs>()

        for (siteRequest in plan.sites) {
            val siteId = siteRequest.siteId
            var cardTypes: List<com.ih.osm.features.cardtype.domain.model.CardType>? = null
            var preclassifiers: List<com.ih.osm.features.preclassifier.domain.model.Preclassifier>? = null
            var priorities: List<com.ih.osm.features.priority.domain.model.Priority>? = null
            var levels: List<com.ih.osm.features.level.domain.model.Level>? = null
            var employees: List<com.ih.osm.features.employee.domain.model.Employee>? = null

            if (CatalogKind.CARD_TYPES in siteRequest.catalogs) {
                when (val result = cardTypeRepository.fetchRemote(siteId)) {
                    is NetworkResult.Success -> cardTypes = result.data
                    is NetworkResult.Failure -> return CatalogSyncResult.Failure(result.error)
                }
                onProgress(CatalogSyncProgress(++completedSteps, plan.totalSteps, CatalogKind.CARD_TYPES, siteId))
            }

            if (CatalogKind.PRECLASSIFIERS in siteRequest.catalogs) {
                when (val result = preclassifierRepository.fetchRemote(siteId)) {
                    is NetworkResult.Success -> preclassifiers = result.data
                    is NetworkResult.Failure -> return CatalogSyncResult.Failure(result.error)
                }
                onProgress(CatalogSyncProgress(++completedSteps, plan.totalSteps, CatalogKind.PRECLASSIFIERS, siteId))
            }

            if (CatalogKind.PRIORITIES in siteRequest.catalogs) {
                when (val result = priorityRepository.fetchRemote(siteId)) {
                    is NetworkResult.Success -> priorities = result.data
                    is NetworkResult.Failure -> return CatalogSyncResult.Failure(result.error)
                }
                onProgress(CatalogSyncProgress(++completedSteps, plan.totalSteps, CatalogKind.PRIORITIES, siteId))
            }

            if (CatalogKind.LEVELS in siteRequest.catalogs) {
                when (val result = levelRepository.fetchRemote(siteId)) {
                    is NetworkResult.Success -> levels = result.data
                    is NetworkResult.Failure -> return CatalogSyncResult.Failure(result.error)
                }
                onProgress(CatalogSyncProgress(++completedSteps, plan.totalSteps, CatalogKind.LEVELS, siteId))
            }

            if (CatalogKind.EMPLOYEES in siteRequest.catalogs) {
                when (val result = employeeRepository.fetchRemote(siteId)) {
                    is NetworkResult.Success -> employees = result.data
                    is NetworkResult.Failure -> return CatalogSyncResult.Failure(result.error)
                }
                onProgress(CatalogSyncProgress(++completedSteps, plan.totalSteps, CatalogKind.EMPLOYEES, siteId))
            }

            downloaded += SiteCatalogs(siteId, cardTypes, preclassifiers, priorities, levels, employees)
        }

        val siteScope = plan.siteScope.distinct().sorted().joinToString(",")
        catalogSyncRepository.replaceAllTransactionally(
            userId = plan.userId,
            siteScope = siteScope,
            completedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
            catalogs = downloaded,
            resetAllBeforeSave = plan.resetAllBeforeSave,
        )
        return CatalogSyncResult.Success
    }
}

class BuildCatalogSyncPlanUseCase(
    private val cardTypes: CardTypeRepository,
    private val preclassifiers: PreclassifierRepository,
    private val priorities: PriorityRepository,
    private val levels: LevelRepository,
    private val employees: EmployeeRepository,
    private val catalogSyncRepository: CatalogSyncRepository,
) {
    operator fun invoke(userId: Long, siteIds: List<Long>): CatalogSyncPlan {
        val distinctSiteIds = siteIds.distinct()
        if (distinctSiteIds.isEmpty()) {
            return CatalogSyncPlan(userId, emptyList(), emptyList(), false)
        }

        val expectedScope = distinctSiteIds.sorted().joinToString(",")
        val metadata = catalogSyncRepository.getMetadata(userId)
        if (metadata == null || metadata.siteScope != expectedScope) {
            return fullPlan(userId, distinctSiteIds)
        }

        val missing = distinctSiteIds.mapNotNull { siteId ->
            val kinds = buildSet {
                if (!isComplete(siteId, CatalogKind.CARD_TYPES, cardTypes.count(siteId))) {
                    add(CatalogKind.CARD_TYPES)
                }
                if (!isComplete(siteId, CatalogKind.PRECLASSIFIERS, preclassifiers.count(siteId))) {
                    add(CatalogKind.PRECLASSIFIERS)
                }
                if (!isComplete(siteId, CatalogKind.PRIORITIES, priorities.count(siteId))) {
                    add(CatalogKind.PRIORITIES)
                }
                if (!isComplete(siteId, CatalogKind.LEVELS, levels.count(siteId))) {
                    add(CatalogKind.LEVELS)
                }
                if (!isComplete(siteId, CatalogKind.EMPLOYEES, employees.count(siteId))) {
                    add(CatalogKind.EMPLOYEES)
                }
            }
            kinds.takeIf(Set<CatalogKind>::isNotEmpty)?.let { SiteCatalogSyncRequest(siteId, it) }
        }
        if (missing.isNotEmpty()) return CatalogSyncPlan(userId, distinctSiteIds, missing, false)

        val age = Clock.System.now().toEpochMilliseconds() - metadata.completedAtEpochMs
        return if (age >= REFRESH_INTERVAL_MS) {
            fullPlan(userId, distinctSiteIds)
        } else {
            CatalogSyncPlan(userId, distinctSiteIds, emptyList(), false)
        }
    }

    fun manual(
        userId: Long,
        siteIds: List<Long>,
        catalogs: Set<CatalogKind>,
    ): CatalogSyncPlan = CatalogSyncPlan(
        userId = userId,
        siteScope = siteIds.distinct(),
        sites = siteIds.distinct().map { SiteCatalogSyncRequest(it, catalogs) },
        resetAllBeforeSave = catalogs == CatalogKind.entries.toSet(),
    )

    private fun fullPlan(userId: Long, siteIds: List<Long>) = CatalogSyncPlan(
        userId = userId,
        siteScope = siteIds,
        sites = siteIds.map { SiteCatalogSyncRequest(it, CatalogKind.entries.toSet()) },
        resetAllBeforeSave = true,
    )

    private fun isComplete(siteId: Long, catalog: CatalogKind, localItemCount: Long): Boolean =
        catalogSyncRepository.isSnapshotComplete(siteId, catalog, localItemCount)

    private companion object {
        const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1_000L
    }
}
