/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ohos.internal

import io.ktor.client.call.UnsupportedContentTypeException
import io.ktor.client.engine.mergeHeaders
import io.ktor.client.engine.ohos.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Convert HttpRequestData to RcpRequestData
 */
@OptIn(InternalAPI::class)
internal suspend fun HttpRequestData.toRcpRequest(config: OhosClientEngineConfig): RcpRequestData {
    // Build headers map
    val headersMap = mutableMapOf<String, String>()
    mergeHeaders(headers, body) { key, value ->
        headersMap[key] = value
    }
    
    return RcpRequestData(
        url = url.toString(),
        method = method.toRcpMethod(),
        headers = headersMap,
        content = body.toByteChannel(),
        contentLength = body.contentLength ?: headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L,
        connectTimeout = getCapabilityOrNull(HttpTimeoutCapability)?.connectTimeoutMillis,
        executionContext = executionContext,
        proxy = null,  // RCP doesn't support proxy configuration
        sslVerify = config.sslVerify,
        caPath = config.caPath,
        caInfo = config.caInfo,
        forceProxyTunneling = false  // Not supported in RCP
    )
}

/**
 * Convert OutgoingContent to ByteReadChannel
 * Reuses the same logic as Curl implementation
 */
@OptIn(DelicateCoroutinesApi::class)
private suspend fun OutgoingContent.toByteChannel(): ByteReadChannel = when (this@toByteChannel) {
    is OutgoingContent.ByteArrayContent -> {
        val bytes = bytes()
        ByteReadChannel(bytes, 0, bytes.size)
    }

    is OutgoingContent.WriteChannelContent -> GlobalScope.writer(currentCoroutineContext()) {
        writeTo(channel)
    }.channel

    is OutgoingContent.ReadChannelContent -> readFrom()
    is OutgoingContent.NoContent -> ByteReadChannel.Empty
    is OutgoingContent.ContentWrapper -> delegate().toByteChannel()
    is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this@toByteChannel)
}
