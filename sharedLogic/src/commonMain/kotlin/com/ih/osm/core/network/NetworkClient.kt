package com.ih.osm.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.http.content.OutgoingContent
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

internal class NetworkClient(
    private val httpClient: HttpClient,
) {
    suspend inline fun <reified Response> get(
        endpoint: String,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): NetworkResult<Response> = safeRequest {
        httpClient.get(endpoint, configure)
    }

    suspend inline fun <reified Request, reified Response> post(
        endpoint: String,
        body: Request,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): NetworkResult<Response> = safeRequest {
        httpClient.post(endpoint) {
            setBody(body)
            configure()
        }
    }

    suspend inline fun <reified Response> postContent(
        endpoint: String,
        body: OutgoingContent,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): NetworkResult<Response> = safeRequest {
        httpClient.post(endpoint) {
            setBody(body)
            configure()
        }
    }

    suspend inline fun <reified Request, reified Response> put(
        endpoint: String,
        body: Request,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): NetworkResult<Response> = safeRequest {
        httpClient.put(endpoint) {
            setBody(body)
            configure()
        }
    }

    suspend inline fun <reified Request, reified Response> patch(
        endpoint: String,
        body: Request,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): NetworkResult<Response> = safeRequest {
        httpClient.patch(endpoint) {
            setBody(body)
            configure()
        }
    }

    suspend inline fun <reified Response> delete(
        endpoint: String,
        noinline configure: HttpRequestBuilder.() -> Unit = {},
    ): NetworkResult<Response> = safeRequest {
        httpClient.delete(endpoint, configure)
    }

    suspend inline fun <reified Response> safeRequest(
        request: () -> HttpResponse,
    ): NetworkResult<Response> = try {
        val response = request()
        if (response.status.isSuccess()) {
            NetworkResult.Success(
                data = response.body(),
                statusCode = response.status.value,
            )
        } else {
            val apiError = runCatching { response.body<ApiErrorResponse>() }.getOrNull()
            val kind = if (response.status.value >= 500) {
                NetworkErrorKind.SERVER
            } else {
                NetworkErrorKind.CLIENT
            }
            NetworkResult.Failure(
                NetworkError(
                    kind = kind,
                    message = apiError?.message
                        ?: apiError?.error
                        ?: response.status.description,
                    statusCode = response.status.value,
                ),
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (serialization: SerializationException) {
        NetworkResult.Failure(
            NetworkError(
                kind = NetworkErrorKind.SERIALIZATION,
                message = serialization.message ?: "Invalid server response.",
            ),
        )
    } catch (io: IOException) {
        NetworkResult.Failure(
            NetworkError(
                kind = NetworkErrorKind.CONNECTIVITY,
                message = "Unable to connect to the server.",
            ),
        )
    } catch (throwable: Throwable) {
        NetworkResult.Failure(
            NetworkError(
                kind = NetworkErrorKind.UNKNOWN,
                message = throwable.message ?: "Unexpected network error.",
            ),
        )
    }
}
