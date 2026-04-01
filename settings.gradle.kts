plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
rootProject.name = "pocketAI"
include(":app")
include(":pocket-core")
include(":pocket-api")
include(":pocket-models-llm")
include(":pocket-orchestration")
include(":feature-chatbot")
include(":pocket-whisper")
