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
            preclassifiersWithData = setOf(2L),
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
        preclassifiersWithData: Set<Long> = setOf(1L, 2L),
    ) {
        private val alwaysAvailable = setOf(1L, 2L)
        val useCase = BuildCatalogSyncPlanUseCase(
            cardTypes = FakeCardTypeRepository(alwaysAvailable),
            preclassifiers = FakePreclassifierRepository(preclassifiersWithData),
            priorities = FakePriorityRepository(alwaysAvailable),
            levels = FakeLevelRepository(alwaysAvailable),
            employees = FakeEmployeeRepository(alwaysAvailable),
            catalogSyncRepository = FakeCatalogSyncRepository(metadata),
        )
    }

    private class FakeCardTypeRepository(private val dataSites: Set<Long>) : CardTypeRepository {
        override suspend fun fetchRemote(siteId: Long) = NetworkResult.Success(emptyList<CardType>(), 200)
        override fun getAll(siteId: Long) = emptyList<CardType>()
        override fun hasData(siteId: Long) = siteId in dataSites
        override fun replaceAll(siteId: Long, items: List<CardType>) = Unit
        override fun deleteAll() = Unit
    }

    private class FakePreclassifierRepository(private val dataSites: Set<Long>) : PreclassifierRepository {
        override suspend fun fetchRemote(siteId: Long) = NetworkResult.Success(emptyList<Preclassifier>(), 200)
        override fun getAll(siteId: Long) = emptyList<Preclassifier>()
        override fun hasData(siteId: Long) = siteId in dataSites
        override fun replaceAll(siteId: Long, items: List<Preclassifier>) = Unit
        override fun deleteAll() = Unit
    }

    private class FakePriorityRepository(private val dataSites: Set<Long>) : PriorityRepository {
        override suspend fun fetchRemote(siteId: Long) = NetworkResult.Success(emptyList<Priority>(), 200)
        override fun getAll(siteId: Long) = emptyList<Priority>()
        override fun hasData(siteId: Long) = siteId in dataSites
        override fun replaceAll(siteId: Long, items: List<Priority>) = Unit
        override fun deleteAll() = Unit
    }

    private class FakeLevelRepository(private val dataSites: Set<Long>) : LevelRepository {
        override suspend fun fetchRemote(siteId: Long) = NetworkResult.Success(emptyList<Level>(), 200)
        override fun getAll(siteId: Long) = emptyList<Level>()
        override fun hasData(siteId: Long) = siteId in dataSites
        override fun replaceAll(siteId: Long, items: List<Level>) = Unit
        override fun deleteAll() = Unit
    }

    private class FakeEmployeeRepository(private val dataSites: Set<Long>) : EmployeeRepository {
        override suspend fun fetchRemote(siteId: Long) = NetworkResult.Success(emptyList<Employee>(), 200)
        override fun getAll(siteId: Long) = emptyList<Employee>()
        override fun hasData(siteId: Long) = siteId in dataSites
        override fun replaceAll(siteId: Long, items: List<Employee>) = Unit
        override fun deleteAll() = Unit
    }

    private class FakeCatalogSyncRepository(
        private val metadata: CatalogSyncMetadata?,
    ) : CatalogSyncRepository {
        override fun replaceAllTransactionally(
            userId: Long,
            siteScope: String,
            completedAtEpochMs: Long,
            catalogs: List<SiteCatalogs>,
            resetAllBeforeSave: Boolean,
        ) = Unit

        override fun getMetadata(userId: Long) = metadata
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
