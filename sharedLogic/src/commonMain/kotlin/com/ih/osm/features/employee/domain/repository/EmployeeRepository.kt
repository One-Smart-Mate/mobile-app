package com.ih.osm.features.employee.domain.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.features.employee.domain.model.Employee

interface EmployeeRepository {
    suspend fun fetchRemote(siteId: Long): NetworkResult<List<Employee>>
    fun getAll(siteId: Long): List<Employee>
    fun hasData(siteId: Long): Boolean
    fun replaceAll(siteId: Long, items: List<Employee>)
    fun deleteAll()
}
