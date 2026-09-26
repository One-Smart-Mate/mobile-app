package com.ih.osm.features.catalog.domain.usecase

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.cardtype.domain.model.CardType
import com.ih.osm.features.cardtype.domain.repository.CardTypeRepository
import com.ih.osm.features.catalog.domain.model.CatalogKind
import com.ih.osm.features.catalog.domain.model.CatalogSyncMetadata
import com.ih.osm.features.catalog.domain.model.SiteCatalogs
import com.ih.osm.features.catalog.domain.repository.CatalogSyncRepository
import com.ih.osm.features.employee.domain.model.Employee
import com.ih.osm.features.employee.domain.repository.EmployeeRepository
import com.ih.osm.features.level.domain.model.Level
import com.ih.osm.features.level.domain.repository.LevelRepository
import com.ih.osm.features.preclassifier.domain.model.Preclassifier
import com.ih.osm.features.preclassifier.domain.repository.PreclassifierRepository
import com.ih.osm.features.priority.domain.model.Priority
import com.ih.osm.features.priority.domain.repository.PriorityRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class BuildCatalogSyncPlanUseCaseTest {
    @Test
    fun syncsOnlyTheMissingCatalogForTheAffectedSite() {
        val fixture = Fixture(
            metadata = recentMetadata(),
            incompleteSnapshots = setOf(1L to CatalogKind.PRECLASSIFIERS),
        )

        val plan = fixture.useCase(userId = USER_ID, siteIds = listOf(1L, 2L))

        assertFalse(plan.resetAllBeforeSave)
        assertEquals(listOf(1L, 2L), plan.siteScope)
        assertEquals(1, plan.sites.size)
        assertEquals(1L, plan.sites.single().siteId)
        assertEquals(setOf(CatalogKind.PRECLASSIFIERS), plan.sites.single().catalogs)
    }

    @Test
    fun returnsAnEmptyPlanWhenEveryCatalogIsAvailableAndFresh() {
        val plan = Fixture(metadata = recentMetadata()).useCase(
            userId = USER_ID,
            siteIds = listOf(1L, 2L),
        )

        assertTrue(plan.isEmpty)
    }

    @Test
    fun acceptsAnEmptyCatalogThatWasSuccessfullySynchronized() {
        val plan = Fixture(
            metadata = recentMetadata(),
            emptySnapshots = setOf(1L to CatalogKind.PRECLASSIFIERS),
        ).useCase(userId = USER_ID, siteIds = listOf(1L, 2L))

        assertTrue(plan.isEmpty)
    }

    @Test
    fun manualPlanCanRefreshOnlySelectedCatalogs() {
        val useCase = Fixture(metadata = recentMetadata()).useCase

        val plan = useCase.manual(
            userId = USER_ID,
            siteIds = listOf(1L, 2L),
            catalogs = setOf(CatalogKind.LEVELS, CatalogKind.PRIORITIES),
        )

        assertFalse(plan.resetAllBeforeSave)
        assertEquals(2, plan.sites.size)
        assertTrue(plan.sites.all { it.catalogs == setOf(CatalogKind.LEVELS, CatalogKind.PRIORITIES) })
    }

    private class Fixture(
        metadata: CatalogSyncMetadata?,
        incompleteSnapshots: Set<Pair<Long, CatalogKind>> = emptySet(),
        emptySnapshots: Set<Pair<Long, CatalogKind>> = emptySet(),
    ) {
        private val defaultCounts = mapOf(1L to 1L, 2L to 1L)
        private val preclassifierCounts = defaultCounts.mapValues { (siteId, count) ->
            if (siteId to CatalogKind.PRECLASSIFIERS in emptySnapshots) 0L else count
        }
        val useCase = BuildCatalogSyncPlanUseCase(
            cardTypes = FakeCardTypeRepository(defaultCounts),
            preclassifiers = FakePreclassifierRepository(preclassifierCounts),
            priorities = FakePriorityRepository(defaultCounts),
            levels = FakeLevelRepository(defaultCounts),
            employees = FakeEmployeeRepository(defaultCounts),
            catalogSyncRepository = FakeCatalogSyncRepository(
                metadata = metadata,
                incompleteSnapshots = incompleteSnapshots,
            ),
        )
    }

    private class FakeCardTypeRepository(private val counts: Map<Long, Long>) : CardTypeRepository {
        override suspend fun fetchRemote(siteId: Long) = NetworkResult.Success(emptyList<CardType>(), 200)
        override fun getAll(siteId: Long) = emptyList<CardType>()
        override fun count(siteId: Long) = counts[siteId] ?: 0L
        override fun replaceAll(siteId: Long, items: List<CardType>) = Unit
        override fun deleteAll() = Unit
    }

    private class FakePreclassifierRepository(private val counts: Map<Long, Long>) : PreclassifierRepository {
        override suspend fun fetchRemote(siteId: Long) = NetworkResult.Success(emptyList<Preclassifier>(), 200)
        override fun getAll(siteId: Long) = emptyList<Preclassifier>()
        override fun count(siteId: Long) = counts[siteId] ?: 0L
        override fun replaceAll(siteId: Long, items: List<Preclassifier>) = Unit
        override fun deleteAll() = Unit
    }

    private class FakePriorityRepository(private val counts: Map<Long, Long>) : PriorityRepository {
        override suspend fun fetchRemote(siteId: Long) = NetworkResult.Success(emptyList<Priority>(), 200)
        override fun getAll(siteId: Long) = emptyList<Priority>()
        override fun count(siteId: Long) = counts[siteId] ?: 0L
        override fun replaceAll(siteId: Long, items: List<Priority>) = Unit
        override fun deleteAll() = Unit
    }

    private class FakeLevelRepository(private val counts: Map<Long, Long>) : LevelRepository {
        override suspend fun fetchRemote(siteId: Long) = NetworkResult.Success(emptyList<Level>(), 200)
        override fun getAll(siteId: Long) = emptyList<Level>()
        override fun count(siteId: Long) = counts[siteId] ?: 0L
        override fun replaceAll(siteId: Long, items: List<Level>) = Unit
        override fun deleteAll() = Unit
    }

    private class FakeEmployeeRepository(private val counts: Map<Long, Long>) : EmployeeRepository {
        override suspend fun fetchRemote(siteId: Long) = NetworkResult.Success(emptyList<Employee>(), 200)
        override fun getAll(siteId: Long) = emptyList<Employee>()
        override fun count(siteId: Long) = counts[siteId] ?: 0L
        override fun replaceAll(siteId: Long, items: List<Employee>) = Unit
        override fun deleteAll() = Unit
    }

    private class FakeCatalogSyncRepository(
        private val metadata: CatalogSyncMetadata?,
        private val incompleteSnapshots: Set<Pair<Long, CatalogKind>> = emptySet(),
    ) : CatalogSyncRepository {
        override fun replaceAllTransactionally(
            userId: Long,
            siteScope: String,
            completedAtEpochMs: Long,
            catalogs: List<SiteCatalogs>,
            resetAllBeforeSave: Boolean,
        ) = Unit

        override fun getMetadata(userId: Long) = metadata
        override fun isSnapshotComplete(siteId: Long, catalog: CatalogKind, localItemCount: Long) =
            siteId to catalog !in incompleteSnapshots
        override fun invalidate(userId: Long) = Unit
    }

    private companion object {
        const val USER_ID = 10L

        fun recentMetadata() = CatalogSyncMetadata(
            userId = USER_ID,
            siteScope = "1,2",
            completedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
        )
    }
}
