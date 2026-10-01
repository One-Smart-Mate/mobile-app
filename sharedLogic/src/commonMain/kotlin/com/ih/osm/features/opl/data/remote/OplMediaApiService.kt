package com.ih.osm.features.opl.data.remote

import com.ih.osm.core.auth.TokenStorage
import com.ih.osm.core.config.AppConfig
import com.ih.osm.core.network.NetworkError
import com.ih.osm.core.network.NetworkErrorKind
import com.ih.osm.core.network.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Streams public storage or same-origin authenticated media, without leaking tokens to storage hosts. */
internal class OplMediaApiService(private val config: AppConfig, private val tokens: TokenStorage) {
    suspend fun read(
        reference: String,
        onChunk: (ByteArray) -> Unit,
        onBytes: (Long, Long?) -> Unit = { _, _ -> },
    ): NetworkResult<Unit> {
        val client = HttpClient {
            expectSuccess = false
            followRedirects = false
            install(HttpTimeout) { connectTimeoutMillis = 15_000; socketTimeoutMillis = 30_000 }
        }
        try {
            val base = Url(config.baseUrl)
            var target = URLBuilder(base).takeFrom(reference).build()
            repeat(6) {
                validate(target, base)
                var redirect: Url? = null
                val result = client.prepareGet(target) {
                    if (sameOrigin(target, base)) tokens.getToken()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                }.execute { response ->
                    if (response.status.value in setOf(301, 302, 303, 307, 308)) {
                        val next = URLBuilder(target).takeFrom(response.headers[HttpHeaders.Location] ?: error("Missing redirect")).build()
                        require(target.protocol.name != "https" || next.protocol.name == "https") { "Insecure redirect" }
                        redirect = next
                        NetworkResult.Success(Unit, response.status.value)
                    } else if (response.status.value !in 200..299) {
                        NetworkResult.Failure(NetworkError(
                            if (response.status.value >= 500) NetworkErrorKind.SERVER else NetworkErrorKind.CLIENT,
                            "OPL media request failed", response.status.value,
                        ))
                    } else {
                        val expected = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it > 0 }
                        require(expected == null || expected <= MAX_FILE_BYTES) { "OPL media exceeds the download limit" }
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(CHUNK_BYTES)
                        var filled = 0
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = channel.readAvailable(buffer, filled, buffer.size - filled)
                            if (count < 0) break
                            if (count == 0) continue
                            filled += count
                            total += count
                            require(total <= MAX_FILE_BYTES) { "OPL media exceeds the download limit" }
                            if (filled == buffer.size) {
                                onChunk(buffer.copyOf())
                                filled = 0
                                onBytes(total, expected)
                            }
                        }
                        if (filled > 0) onChunk(buffer.copyOf(filled))
                        require(total > 0 && (expected == null || expected == total)) { "Incomplete OPL media" }
                        onBytes(total, expected)
                        NetworkResult.Success(Unit, response.status.value)
                    }
                }
                if (redirect == null) return result
                target = requireNotNull(redirect)
            }
            return failure(NetworkErrorKind.CLIENT, "Too many OPL media redirects")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            return failure(NetworkErrorKind.CONNECTIVITY, "Unable to download OPL media")
        } catch (error: Exception) {
            return failure(NetworkErrorKind.UNKNOWN, error.message ?: "OPL media download failed")
        } finally {
            client.close()
        }
    }

    private fun validate(url: Url, base: Url) {
        require(url.host.isNotBlank() && url.user.isNullOrEmpty() && url.password.isNullOrEmpty())
        require(url.protocol.name == "https" || (url.protocol.name == "http" && sameOrigin(url, base))) {
            "OPL media must use HTTPS"
        }
    }

    private fun sameOrigin(first: Url, second: Url) =
        first.protocol == second.protocol && first.host.equals(second.host, true) && first.port == second.port

    private fun failure(kind: NetworkErrorKind, message: String) = NetworkResult.Failure(NetworkError(kind, message))

    private companion object {
        const val CHUNK_BYTES = 512 * 1024
        const val MAX_FILE_BYTES = 100L * 1024 * 1024
    }
}
