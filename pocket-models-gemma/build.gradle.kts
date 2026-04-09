plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.pocket.models.gemma"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":pocket-core"))
    implementation(project(":pocket-api"))
    // LiteRT-LM core engine (NPU / GPU / CPU fallback in native runtime).
    // Docs sometimes show `com.google.ai.edge:litert-lm:1.0.0-alpha01`; that GAV does not resolve on
    // google() / mavenCentral(). The published Android artifact is `litertlm-android` (same
    // `com.google.ai.edge.litertlm.*` Kotlin API).
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
