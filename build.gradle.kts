import java.util.Properties

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("com.android.asset-pack") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0" apply false
}

// Load gradle/<device|emulator>.properties into each subproject (before its build.gradle.kts).
// Default target is device (Play / physical phone). Emulator script sets -Pgyango.buildTarget=emulator.
val gyangoBuildTarget =
    (
        findProperty("gyango.buildTarget")?.toString()
            ?: System.getenv("GYANGO_BUILD_TARGET")
            ?: "device"
        ).lowercase()
require(gyangoBuildTarget == "device" || gyangoBuildTarget == "emulator") {
    "gyango.buildTarget / GYANGO_BUILD_TARGET must be device or emulator, was: $gyangoBuildTarget"
}
val gyangoTargetPropertiesFile = rootProject.layout.projectDirectory.file("gradle/$gyangoBuildTarget.properties").asFile
require(gyangoTargetPropertiesFile.isFile) {
    "Missing ${gyangoTargetPropertiesFile.absolutePath} (gyango.buildTarget=$gyangoBuildTarget)"
}
val gyangoTargetProperties = Properties().apply {
    gyangoTargetPropertiesFile.reader().use(::load)
}

subprojects {
    beforeEvaluate {
        gyangoTargetProperties.forEach { k, v ->
            val key = k.toString()
            if (!hasProperty(key)) {
                extensions.extraProperties.set(key, v.toString())
            }
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

/** Splits the full LiteRT bundle into five ~500 MiB chunks for on-demand PAD modules. */
tasks.register<Exec>("splitPadBaseModelChunks") {
    group = "gyango"
    description =
        "Writes gyango_pack_base_llm_{0..4}/src/main/assets/models/pad_chunk_{0..4}.bin from gyango_pad_model_source/gemma-4-E2B-it.litertlm"
    workingDir(rootDir)
    commandLine("bash", file("tools/android/split-pad-base-model-into-packs.sh").absolutePath)
}
