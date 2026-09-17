package com.ih.osm.features.auth.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ApiResponse<T>(
    val data: T,
    val status: Int? = null,
    val message: String? = null,
)

@Serializable
internal data class LoginRequest(
    val email: String,
    val password: String,
    val timezone: String,
    val platform: String,
)

@Serializable
internal data class AuthResponse(
    val userId: Long,
    val name: String,
    val email: String,
    val token: String,
    val roles: List<String> = emptyList(),
    val logo: String? = null,
    val companyId: Long,
    val companyName: String,
    val sites: List<SiteDto> = emptyList(),
    @SerialName("app_history") val appHistory: Long,
    val dueDate: String? = null,
)

@Serializable
internal data class SiteDto(
    val id: Long,
    val name: String,
    val logo: String? = null,
)
