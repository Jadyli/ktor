/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ohos.internal

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.locks.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.rcp.*

/**
 * RCP Session handler for managing HTTP requests
 */
@OptIn(ExperimentalForeignApi::class, InternalAPI::class)
internal class RcpSession : Closeable {
    private val session: RcpSessionHandle
    private var requestIdCounter = 0
    private val activeRequests = mutableMapOf<String, RcpRequestHandle>()
    private val lock = SynchronizedObject()
    
    init {
        session = createRcpSession()
    }
    
    /**
     * Execute an RCP request
     */
    suspend fun executeRequest(request: RcpRequestData): RcpSuccess {
        val requestId = synchronized(lock) {
            (++requestIdCounter).toString()
        }
        val completion = CompletableDeferred<RcpSuccess>()
        
        val rcpRequest = buildRcpRequest(request)
        
        synchronized(lock) {
            activeRequests[requestId] = rcpRequest
        }
        
        val callbackContext = RcpCallbackContext(completion, requestId)
        val callback = createResponseCallback(callbackContext)
        
        // Set up cancellation
        request.executionContext.invokeOnCompletion { cause ->
            if (cause != null) {
                cancelRequest(requestId)
            }
        }
        
        // Send request
        val result = HMS_Rcp_Fetch(session, rcpRequest, callback)
        if (result != RCP_OK) {
            synchronized(lock) {
                activeRequests.remove(requestId)
            }
            HMS_Rcp_DestroyRequest(rcpRequest)
            throw Exception("Failed to send RCP request: $result")
        }
        
        return try {
            completion.await()
        } finally {
            synchronized(lock) {
                activeRequests.remove(requestId)
            }
            HMS_Rcp_DestroyRequest(rcpRequest)
        }
    }
    
    /**
     * Cancel a specific request
     */
    private fun cancelRequest(requestId: String) {
        val request = synchronized(lock) {
            activeRequests.remove(requestId)
        } ?: return
        
        HMS_Rcp_CancelRequest(session, request)
        HMS_Rcp_DestroyRequest(request)
    }
    
    /**
     * Build RCP request from request data
     */
    private fun buildRcpRequest(data: RcpRequestData): RcpRequestHandle = memScoped {
        val request = HMS_Rcp_CreateRequest(data.url)
            ?: error("Failed to create RCP request")
        
        val req = request.pointed
        
        // Set method
        req.method = data.method.cstr.ptr
        
        // Set headers
        val headers = HMS_Rcp_CreateHeaders()
        if (headers != null) {
            data.headers.forEach { (key, value) ->
                HMS_Rcp_SetHeaderValue(headers, key, value)
            }
            req.headers = headers
        }
        
        // Set content if present
        if (data.content != null && data.contentLength > 0) {
            val contentBytes = runBlocking {
                data.content.readRemaining().readBytes()
            }

            val rcpContent = alloc<Rcp_RequestContent>()
            rcpContent.type = Rcp_ContentType.RCP_CONTENT_TYPE_STRING
            rcpContent.data.contentStr.buffer = contentBytes.refTo(0).getPointer(this)
            rcpContent.data.contentStr.length = contentBytes.size.toUInt()
            
            req.content = rcpContent.ptr
        }
        
        // Set configuration (timeout, SSL, etc.)
        val needsConfig = data.connectTimeout != null || 
                         !data.sslVerify || 
                         data.caPath != null || 
                         data.caInfo != null
        
        if (needsConfig) {
            val config = alloc<Rcp_Configuration>()
            
            // Set timeout
            data.connectTimeout?.let { timeout ->
                config.transferConfiguration.timeout.connectMs = timeout.toUInt()
            }

            // Set SSL verification
            if (!data.sslVerify) {
                config.securityConfiguration.remoteValidationType = 
                    Rcp_RemoteValidationType.RCP_REMOTE_VALIDATION_SKIP
            }
            
            // Set CA path/info
            data.caPath?.let { path ->
                config.securityConfiguration.certificateAuthority.folderPath = 
                    path.cstr.ptr
            }
            data.caInfo?.let { info ->
                config.securityConfiguration.certificateAuthority.filePath = 
                    info.cstr.ptr
            }
            
            req.configuration = config.ptr
        }
        
        request
    }
    
    /**
     * Create RCP session
     */
    private fun createRcpSession(): RcpSessionHandle {
        memScoped {
            val errCode = alloc<UIntVar>()
            val config = alloc<Rcp_SessionConfiguration>().apply {
                type = RCP_SESSION_TYPE_HTTP
            }
            
            val session = HMS_Rcp_CreateSession(config.ptr, errCode.ptr)
                ?: error("Failed to create RCP session: ${errCode.value}")
            
            return session
        }
    }
    
    /**
     * Close the RCP session and cleanup resources
     */
    override fun close() {
        // Cancel all active requests
        synchronized(lock) {
            activeRequests.values.forEach { request ->
                HMS_Rcp_CancelRequest(session, request)
                HMS_Rcp_DestroyRequest(request)
            }
            activeRequests.clear()
        }
        
        // Close session
        memScoped {
            val sessionPtr = alloc<CPointerVar<cnames.structs.Rcp_Session>>().apply {
                value = session
            }
            HMS_Rcp_CloseSession(sessionPtr.ptr).rcpVerify("Failed to close RCP session")
        }
    }
}
