pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.13.2"
        id("com.android.library") version "8.13.2"
        id("org.jetbrains.kotlin.android") version "2.3.0"
        id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
        id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
rootProject.name = "pocketAI"
include(":app")
include(":pocket-core")
include(":pocket-api")
include(":pocket-models-gemma")
include(":pocket-orchestration")
include(":feature-chatbot")
include(":ai-pack-teacher-3gb")
include(":ai-pack-teacher-6gb")
include(":ai-pack-teacher-12gb")
