/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("kotlinx-serialization")
}

kotlin {

    createCInterop("libcurl", listOf("ohosArm64")) {
        definitionFile = File(projectDir, "ohosArm64/interop/libcurl.def")
        includeDirs(File(projectDir, "ohosArm64/interop/include/"))
    }
    createCInterop("rcp", listOf("ohosArm64")) {
        definitionFile = File(projectDir, "ohosArm64/interop/rcp.def")
        includeDirs(File(projectDir, "ohosArm64/interop/include/"))
    }

    sourceSets {
        ohosArm64Main {
            dependencies {
                api(project(":ktor-client:ktor-client-core"))
                api(project(":ktor-http:ktor-http-cio"))
            }
        }
    }
}
