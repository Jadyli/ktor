/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ohos

import io.ktor.client.engine.ohos.internal.*
import io.ktor.utils.io.core.Closeable
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * RCP Processor for handling RCP-based network requests
 * This is an alternative to CurlProcessor using HarmonyOS native RCP API
 */
internal class RcpProcessor(coroutineContext: CoroutineContext) : Closeable {
    private val rcpScope = CoroutineScope(coroutineContext)
    private val session: RcpSession = RcpSession()
    
    /**
     * Execute an RCP request
     */
    suspend fun executeRequest(request: RcpRequestData): RcpSuccess {
        return session.executeRequest(request)
    }
    
    /**
     * Close the processor and cleanup resources
     */
    override fun close() {
        session.close()
        rcpScope.coroutineContext[Job]?.cancel()
    }
}
