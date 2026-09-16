package com.ih.osm.core.network

sealed interface NetworkResult<out T> {
    data class Success<T>(
        val data: T,
        val statusCode: Int,
    ) : NetworkResult<T>

    data class Failure(
        val error: NetworkError,
    ) : NetworkResult<Nothing>
}

data class NetworkError(
    val kind: NetworkErrorKind,
    val message: String,
    val statusCode: Int? = null,
)

enum class NetworkErrorKind {
    CLIENT,
    SERVER,
    CONNECTIVITY,
    SERIALIZATION,
    UNKNOWN,
}
