/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ohos.internal

import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.InternalAPI
import kotlinx.cinterop.*
import platform.rcp.*

@OptIn(ExperimentalForeignApi::class)
internal typealias RcpSessionHandle = CPointer<cnames.structs.Rcp_Session>

@OptIn(ExperimentalForeignApi::class)
internal typealias RcpRequestHandle = CPointer<Rcp_Request>

@OptIn(ExperimentalForeignApi::class)
internal typealias RcpResponseHandle = CPointer<Rcp_Response>

@OptIn(ExperimentalForeignApi::class)
internal typealias RcpHeadersHandle = CPointer<cnames.structs.Rcp_Headers>

/**
 * Verify RCP operation result
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UInt.rcpVerify(message: String = "RCP operation failed") {
    if (this != RCP_OK.toUInt()) {
        error("$message: Error code $this")
    }
}

/**
 * Convert HttpRequestData headers to RCP headers
 */
@OptIn(ExperimentalForeignApi::class, InternalAPI::class)
internal fun HttpRequestData.headersToRcp(): RcpHeadersHandle {
    val headers = HMS_Rcp_CreateHeaders() 
        ?: error("Failed to create RCP headers")
    
    mergeHeaders(this.headers, body) { key, value ->
        HMS_Rcp_SetHeaderValue(headers, key, value).rcpVerify("Failed to set header $key")
    }
    
    return headers
}

/**
 * Convert RCP headers to ByteArray for parsing
 */
@OptIn(ExperimentalForeignApi::class)
internal fun RcpHeadersHandle.toByteArray(): ByteArray {
    val entries = HMS_Rcp_GetHeaderEntries(this) ?: return byteArrayOf()
    
    val result = buildString {
        var current: CPointer<Rcp_HeaderEntry>? = entries
        while (current != null) {
            val entry = current.pointed
            val name = entry.key?.toKString() ?: ""
            
            // Get header value
            val valuePtr = entry.value
            if (valuePtr != null) {
                var valueEntry: CPointer<Rcp_HeaderValue>? = valuePtr
                while (valueEntry != null) {
                    val value = valueEntry.pointed.value?.toKString() ?: ""
                    if (name.isNotEmpty() && value.isNotEmpty()) {
                        appendLine("$name: $value")
                    }
                    valueEntry = valueEntry.pointed.next
                }
            }
            
            current = entry.next
        }
    }
    
    HMS_Rcp_DestroyHeaderEntries(entries)
    return result.encodeToByteArray()
}

/**
 * Map RCP session type to HTTP protocol version
 */
@OptIn(ExperimentalForeignApi::class)
internal fun UInt.fromRcp(): HttpProtocolVersion = when (this) {
    RCP_SESSION_TYPE_HTTP -> HttpProtocolVersion.HTTP_1_1
    else -> HttpProtocolVersion.HTTP_1_1
}

/**
 * Map HTTP method to RCP method constant
 */
@OptIn(ExperimentalForeignApi::class)
internal fun HttpMethod.toRcpMethod(): String = when (this) {
    HttpMethod.Get -> RCP_METHOD_GET
    HttpMethod.Post -> RCP_METHOD_POST
    HttpMethod.Put -> RCP_METHOD_PUT
    HttpMethod.Delete -> RCP_METHOD_DELETE
    HttpMethod.Head -> RCP_METHOD_HEAD
    HttpMethod.Options -> RCP_METHOD_OPTIONS
    HttpMethod.Patch -> RCP_METHOD_PATCH
    else -> this.value
}
