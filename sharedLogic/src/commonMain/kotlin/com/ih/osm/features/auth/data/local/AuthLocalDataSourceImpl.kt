package com.ih.osm.features.auth.data.local

import com.ih.osm.database.AppDatabase
import com.ih.osm.features.auth.domain.model.AuthenticatedUser
import com.ih.osm.features.auth.domain.model.UserSite

internal class AuthLocalDataSourceImpl(
    private val database: AppDatabase,
) : AuthLocalDataSource {
    override suspend fun saveUser(user: AuthenticatedUser) {
        database.transaction {
            clearTables()
            database.userSessionQueries.insertUser(
                user_id = user.id,
                name = user.name,
                email = user.email,
                logo = user.logo,
                company_id = user.companyId,
                company_name = user.companyName,
                app_history = user.appHistory,
                due_date = user.dueDate,
            )
            user.roles.forEach(database.userSessionQueries::insertRole)
            user.sites.forEach { site ->
                database.userSessionQueries.insertSite(site.id, site.name, site.logo)
            }
        }
    }

    override suspend fun getUser(): AuthenticatedUser? {
        val stored = database.userSessionQueries.selectUser().executeAsOneOrNull() ?: return null
        return AuthenticatedUser(
            id = stored.user_id,
            name = stored.name,
            email = stored.email,
            roles = database.userSessionQueries.selectRoles().executeAsList(),
            logo = stored.logo,
            companyId = stored.company_id,
            companyName = stored.company_name,
            sites = database.userSessionQueries.selectSites().executeAsList().map { site ->
                UserSite(id = site.id, name = site.name, logo = site.logo)
            },
            appHistory = stored.app_history,
            dueDate = stored.due_date,
        )
    }

    override suspend fun deleteUser() {
        database.transaction { clearTables() }
    }

    private fun clearTables() {
        database.userSessionQueries.deleteRoles()
        database.userSessionQueries.deleteSites()
        database.userSessionQueries.deleteUser()
    }
}
