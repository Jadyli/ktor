/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `kotlin-dsl`
}

repositories {
    maven {
        setUrl("https://packages.aliyun.com/66b7f208953179b1ec5f5db8/maven/2486646-snapshot-3qr5na")
        credentials {
            username = "66b7e3a18043c5959c0c01e2"
            password = "no2udBiPX]2("
        }
    }
    mavenCentral()
    gradlePluginPortal()
    maven("https://mirrors.tencent.com/nexus/repository/maven-tencent")
    maven("https://mirrors.tencent.com/nexus/repository/maven-public")

    mavenLocal()
}

dependencies {
    implementation(libs.develocity)
    implementation(libs.develocity.commonCustomUserData)
}
