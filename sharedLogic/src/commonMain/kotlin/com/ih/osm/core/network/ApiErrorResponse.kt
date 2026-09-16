package com.ih.osm.core.network

import kotlinx.serialization.Serializable

@Serializable
internal data class ApiErrorResponse(
    val message: String? = null,
    val error: String? = null,
    val errorCode: String? = null,
)
