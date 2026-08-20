// Copyright 2026 soe1hom-arch
// SPDX-License-Identifier: Apache-2.0

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.soniclab.dsp"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        externalNativeBuild {
            cmake {
                // Oboe's prefab library is built against a shared STL.
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    ndkVersion = "27.2.12479018"

    buildFeatures {
        // Oboe ships headers + liboboe.so as an Android prefab module; this
        // makes CMake's find_package(oboe) resolve oboe::oboe for the native
        // engine.
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.oboe)
    implementation(libs.org.tensorflow.lite)

    testImplementation(libs.junit)
}
