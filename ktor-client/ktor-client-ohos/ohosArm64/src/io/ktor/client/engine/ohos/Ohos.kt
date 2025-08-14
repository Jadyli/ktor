/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ohos

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.engines
import io.ktor.utils.io.InternalAPI
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import libcurl.CURL_GLOBAL_ALL
import libcurl.curl_global_init

@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val curlGlobalInitReturnCode = curlInitBridge()

@OptIn(ExperimentalForeignApi::class)
internal fun curlInitBridge(): Int = curl_global_init(CURL_GLOBAL_ALL.convert()).convert()

@OptIn(ExperimentalStdlibApi::class)
@Suppress("unused", "DEPRECATION")
@EagerInitialization
private val initHook = Ohos

@OptIn(InternalAPI::class)
public data object Ohos : HttpClientEngineFactory<OhosClientEngineConfig> {
    init {
        engines.append(this)
    }

    override fun create(block: OhosClientEngineConfig.() -> Unit): HttpClientEngine {
        if (curlGlobalInitReturnCode != 0) {
            throw RuntimeException("curl_global_init() returned non-zero verify: $curlGlobalInitReturnCode")
        }

        return OhosClientEngine(OhosClientEngineConfig().apply(block))
    }
}
