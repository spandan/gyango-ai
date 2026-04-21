plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

/** From `gradle/device.properties` or `gradle/emulator.properties` (see root settings.gradle.kts). */
val gyangoEmulatorLitertWorkarounds: Boolean =
    project.findProperty("gyango.emulatorLitertWorkarounds")?.toString().equals("true", ignoreCase = true)

android {
    namespace = "ai.gyango.litert"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("proguard-rules.pro")
        buildConfigField(
            "boolean",
            "ENABLE_EMULATOR_LITERT_WORKAROUNDS",
            gyangoEmulatorLitertWorkarounds.toString(),
        )
    }

    buildFeatures {
        buildConfig = true
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
    implementation(project(":gyango-core"))
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
