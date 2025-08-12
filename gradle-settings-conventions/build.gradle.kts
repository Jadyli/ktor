/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    `kotlin-dsl`
}

repositories {
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
