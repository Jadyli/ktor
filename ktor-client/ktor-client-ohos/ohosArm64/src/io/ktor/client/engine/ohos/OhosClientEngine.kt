/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ohos

import io.ktor.client.engine.*
import io.ktor.client.engine.ohos.internal.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers

internal class OhosClientEngine(
    override val config: OhosClientEngineConfig
) : HttpClientEngineBase("ktor-ohos") {
    override val dispatcher = Dispatchers.Unconfined

    override val supportedCapabilities = setOf(HttpTimeoutCapability, SSECapability)

    private val curlProcessor by lazy { CurlProcessor(coroutineContext) }
    private val rcpProcessor by lazy { RcpProcessor(coroutineContext) }

    @InternalAPI
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val requestTime = GMTDate()

        if (config.useRcp) {
            // Use RCP API
            val rcpRequest = data.toRcpRequest(config)
            val responseData = rcpProcessor.executeRequest(rcpRequest)
            
            val headerBytes = ByteReadChannel(responseData.headersBytes).apply {
                readUTF8Line()
            }
            val rawHeaders = parseHeaders(headerBytes)
            val headers = rawHeaders
                .toBuilder().apply {
                    dropCompressionHeaders(data.method, data.attributes)
                }.build()

            rawHeaders.release()

            val status = HttpStatusCode.fromValue(responseData.status)

            val responseBody: Any = data.attributes.getOrNull(ResponseAdapterAttributeKey)
                ?.adapt(data, status, headers, responseData.bodyChannel, data.body, callContext)
                ?: responseData.bodyChannel

            return HttpResponseData(
                status,
                requestTime,
                headers,
                responseData.version.fromRcp(),
                responseBody,
                callContext
            )
        } else {
            // Use Curl as fallback
            val curlRequest = data.toCurlRequest(config)
            val responseData = curlProcessor.executeRequest(curlRequest)
            
            val headerBytes = ByteReadChannel(responseData.headersBytes).apply {
                readUTF8Line()
            }
            val rawHeaders = parseHeaders(headerBytes)
            val headers = rawHeaders
                .toBuilder().apply {
                    dropCompressionHeaders(data.method, data.attributes)
                }.build()

            rawHeaders.release()

            val status = HttpStatusCode.fromValue(responseData.status)

            val responseBody: Any = data.attributes.getOrNull(ResponseAdapterAttributeKey)
                ?.adapt(data, status, headers, responseData.bodyChannel, data.body, callContext)
                ?: responseData.bodyChannel

            return HttpResponseData(
                status,
                requestTime,
                headers,
                responseData.version.fromCurl(),
                responseBody,
                callContext
            )
        }
    }
    
    override fun close() {
        super.close()
        
        // Close RCP processor if it was used
        // Note: lazy delegates are always initialized when accessed,
        // so we just check the config to avoid unnecessary initialization
        if (config.useRcp) {
            try {
                rcpProcessor.close()
            } catch (e: Exception) {
                // Ignore if processor was never initialized
            }
        }
    }
}

@Deprecated("This exception will be removed in a future release in favor of a better error handling.")
public class CurlIllegalStateException(cause: String) : IllegalStateException(cause)

@Deprecated("This exception will be removed in a future release in favor of a better error handling.")
public class CurlRuntimeException(cause: String) : RuntimeException(cause)
