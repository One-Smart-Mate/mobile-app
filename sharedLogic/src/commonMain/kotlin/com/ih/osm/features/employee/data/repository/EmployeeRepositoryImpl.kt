package com.ih.osm.features.employee.data.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.database.AppDatabase
import com.ih.osm.features.catalog.data.remote.CatalogApiService
import com.ih.osm.features.catalog.data.remote.mapSuccess
import com.ih.osm.features.employee.domain.model.Employee
import com.ih.osm.features.employee.domain.repository.EmployeeRepository

internal class EmployeeRepositoryImpl(
    private val api: CatalogApiService,
    private val database: AppDatabase,
) : EmployeeRepository {
    override suspend fun fetchRemote(siteId: Long): NetworkResult<List<Employee>> =
        api.getEmployees(siteId).mapSuccess { items -> items.map { it.toDomain() } }

    override fun getAll(siteId: Long): List<Employee> =
        database.catalogsQueries.selectEmployeesBySite(siteId).executeAsList().map {
            Employee(it.id, it.name, it.email)
        }

    override fun count(siteId: Long): Long =
        database.catalogsQueries.countEmployeesBySite(siteId).executeAsOne()

    override fun replaceAll(siteId: Long, items: List<Employee>) {
        database.catalogsQueries.deleteEmployeesBySite(siteId)
        items.forEach { database.catalogsQueries.insertEmployee(siteId, it.id, it.name, it.email) }
    }

    override fun deleteAll() {
        database.catalogsQueries.deleteAllEmployees()
    }
}
