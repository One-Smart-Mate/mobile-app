package com.ih.osm.features.auth.domain.model

data class AuthenticatedUser(
    val id: Long,
    val name: String,
    val email: String,
    val roles: List<String>,
    val logo: String?,
    val companyId: Long,
    val companyName: String,
    val sites: List<UserSite>,
    val appHistory: Long,
    val dueDate: String?,
)

data class UserSite(
    val id: Long,
    val name: String,
    val logo: String?,
)
