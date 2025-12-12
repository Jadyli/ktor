/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ohos.internal

import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

/**
 * Data class representing an RCP request
 */
internal data class RcpRequestData(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val content: ByteReadChannel?,
    val contentLength: Long,
    val connectTimeout: Long?,
    val executionContext: Job,
    val proxy: Url? = null,
    val sslVerify: Boolean = true,
    val caPath: String? = null,
    val caInfo: String? = null,
    val forceProxyTunneling: Boolean = false
)

/**
 * Sealed class representing RCP response data
 */
internal sealed class RcpResponseData {
    data class Success(
        val status: Int,
        val version: UInt,
        val headersBytes: ByteArray,
        val bodyChannel: ByteReadChannel
    ) : RcpResponseData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Success

            if (status != other.status) return false
            if (version != other.version) return false
            if (!headersBytes.contentEquals(other.headersBytes)) return false
            if (bodyChannel != other.bodyChannel) return false

            return true
        }

        override fun hashCode(): Int {
            var result = status
            result = 31 * result + version.hashCode()
            result = 31 * result + headersBytes.contentHashCode()
            result = 31 * result + bodyChannel.hashCode()
            return result
        }
    }

    data class Fail(val cause: Throwable) : RcpResponseData()
}

internal typealias RcpSuccess = RcpResponseData.Success
internal typealias RcpFail = RcpResponseData.Fail
