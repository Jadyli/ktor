/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ohos.internal

import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.rcp.*

/**
 * Context object for RCP callbacks
 */
@OptIn(ExperimentalForeignApi::class)
internal class RcpCallbackContext(
    val completion: CompletableDeferred<RcpSuccess>,
    val requestId: String
) {
    fun asStablePointer(): COpaquePointer {
        return StableRef.create(this).asCPointer()
    }
    
    companion object {
        fun fromPointer(ptr: COpaquePointer): RcpCallbackContext {
            return ptr.asStableRef<RcpCallbackContext>().get()
        }
        
        fun dispose(ptr: COpaquePointer) {
            ptr.asStableRef<RcpCallbackContext>().dispose()
        }
    }
}

/**
 * Create RCP response callback object
 */
@OptIn(ExperimentalForeignApi::class)
internal fun createResponseCallback(
    context: RcpCallbackContext
): CValue<Rcp_ResponseCallbackObject> {
    return cValue {
        callback = staticCFunction(::onRcpResponseCallback)
        usrCtx = context.asStablePointer()
    }
}

/**
 * RCP response callback - handles both success and failure
 */
@OptIn(ExperimentalForeignApi::class)
private fun onRcpResponseCallback(
    usrCtx: COpaquePointer?,
    response: CPointer<Rcp_Response>?,
    errorCode: UInt
) {
    if (usrCtx == null) return
    
    val context = RcpCallbackContext.fromPointer(usrCtx)
    
    try {
        if (errorCode != RCP_OK || response == null) {
            val errorMessage = mapRcpErrorCode(errorCode)
            context.completion.completeExceptionally(
                Exception("RCP request failed: $errorMessage (code: $errorCode)")
            )
        } else {
            val result = parseRcpResponse(response)
            context.completion.complete(result)
        }
    } catch (e: Exception) {
        context.completion.completeExceptionally(e)
    } finally {
        RcpCallbackContext.dispose(usrCtx)
    }
}

/**
 * Map RCP error code to human-readable message
 */
@OptIn(ExperimentalForeignApi::class)
private fun mapRcpErrorCode(errorCode: UInt): String = when (errorCode) {
    RCP_BAD_REQUEST -> "Bad Request (400)"
    RCP_UNAUTHORIZED -> "Unauthorized (401)"
    RCP_FORBIDDEN -> "Forbidden (403)"
    RCP_NOT_FOUND -> "Not Found (404)"
    RCP_INTERNAL_ERROR -> "Internal Server Error (500)"
    RCP_BAD_GATEWAY -> "Bad Gateway (502)"
    RCP_UNAVAILABLE -> "Service Unavailable (503)"
    RCP_GATEWAY_TIMEOUT -> "Gateway Timeout (504)"
    RCP_CLIENT_TIMEOUT -> "Client Timeout (408)"
    else -> "Unknown error"
}

/**
 * Parse RCP response and extract information
 */
@OptIn(ExperimentalForeignApi::class)
private fun parseRcpResponse(
    response: CPointer<Rcp_Response>
): RcpSuccess {
    val resp = response.pointed
    
    // Extract status code
    val statusCode = resp.statusCode.toInt()
    
    // Extract headers
    val headersBytes = resp.headers?.toByteArray() ?: byteArrayOf()
    
    // Extract body from Rcp_Buffer
    val bodyBuffer = resp.body
    val bodyBytes = if (bodyBuffer.buffer != null && bodyBuffer.length > 0u) {
        ByteArray(bodyBuffer.length.toInt()).also {
            bodyBuffer.buffer!!.readBytes(bodyBuffer.length.toInt())
        }
    } else {
        byteArrayOf()
    }
    
    return RcpSuccess(
        status = statusCode,
        version = RCP_SESSION_TYPE_HTTP,
        headersBytes = headersBytes,
        bodyChannel = ByteReadChannel(bodyBytes)
    )
}
