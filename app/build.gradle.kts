plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ai.gyango"
    compileSdk = 35

    /** Opt-in: `./gradlew ... -Pgyango.verboseLogs=true` — large logcat (prompts, perf, PAD noise). Default off for all flavors/buildTypes. */
    val verboseDebugLogs =
        project.findProperty("gyango.verboseLogs")?.toString()?.equals("true", ignoreCase = true) == true

    /** Sum of `pad_chunk_*.bin` sizes; [GyangoPadDelivery] uses this to reject partial merges (tmp ≥512 MiB but missing last pack). */
    val padMergedExpectedBytes = (0..4).sumOf { i ->
        rootProject.file("gyango_pack_base_llm_$i/src/main/assets/models/pad_chunk_$i.bin")
            .takeIf { it.exists() }
            ?.length()
            ?: 0L
    }

    defaultConfig {
        applicationId = "ai.gyango"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.0"

        buildConfigField("String", "LITERT_MODEL_ABSOLUTE_PATH", "\"\"")
        buildConfigField("boolean", "USE_PAD", "false")
        buildConfigField("boolean", "FAKE_ENTITLEMENTS_UNLOCK_ALL", "false")
        buildConfigField("boolean", "VERBOSE_DEBUG_LOGS", "$verboseDebugLogs")
        buildConfigField("long", "PAD_MERGED_MODEL_EXPECTED_BYTES", "${padMergedExpectedBytes}L")
    }

    flavorDimensions += "delivery"
    productFlavors {
        create("localDev") {
            dimension = "delivery"
            buildConfigField("boolean", "USE_PAD", "false")
        }
        create("pad") {
            dimension = "delivery"
            buildConfigField("boolean", "USE_PAD", "true")
        }
    }

    assetPacks += listOf(
        ":gyango_pack_base_llm_0",
        ":gyango_pack_base_llm_1",
        ":gyango_pack_base_llm_2",
        ":gyango_pack_base_llm_3",
        ":gyango_pack_base_llm_4",
    )

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(providers.gradleProperty("GYANGO_UPLOAD_STORE_FILE").get())
            storePassword = providers.gradleProperty("GYANGO_UPLOAD_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("GYANGO_UPLOAD_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("GYANGO_UPLOAD_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "FAKE_ENTITLEMENTS_UNLOCK_ALL", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("boolean", "FAKE_ENTITLEMENTS_UNLOCK_ALL", "true")
        }
    }

    androidResources {
        noCompress += listOf("tflite", "litertlm", "mmproj")
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*/.DS_Store"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
        }
    }

    applicationVariants.configureEach {
        val variantName = name
        outputs.configureEach {
            val abi = filters.find { it.filterType.equals("ABI", ignoreCase = true) }
                ?.identifier
                ?: "universal"
            (this as com.android.build.gradle.internal.api.ApkVariantOutputImpl).outputFileName =
                "gyangoai-${variantName}-${abi}.apk"
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

val resizeAppIcon = tasks.register<Exec>("resizeAppIcon") {
    val iconAsset = file("src/main/assets/images/gyangoAIIcon.webp")
    val outDir = layout.buildDirectory.dir("generated/icon").get().asFile
    val resized = outDir.resolve("gyango_ai_resized.png")

    inputs.file(iconAsset)
    outputs.file(resized)

    commandLine("sips", "-Z", "264", "-s", "format", "png", iconAsset.absolutePath, "-o", resized.absolutePath)

    onlyIf { iconAsset.exists() }
    doFirst { outDir.mkdirs() }
}

val padAppIcon = tasks.register<Exec>("padAppIcon") {
    dependsOn(resizeAppIcon)
    val outDir = layout.buildDirectory.dir("generated/icon").get().asFile
    val resized = outDir.resolve("gyango_ai_resized.png")
    val padded = outDir.resolve("gyango_ai.png")

    inputs.file(resized)
    outputs.file(padded)

    commandLine("sips", "-p", "432", "432", "--padColor", "1565C0", resized.absolutePath, "-o", padded.absolutePath)

    onlyIf { resized.exists() }
}

tasks.register<Copy>("copyAppIcon") {
    dependsOn(padAppIcon)
    val outDir = layout.buildDirectory.dir("generated/icon").get().asFile
    val padded = outDir.resolve("gyango_ai.png")
    val targetDir = file("src/main/res/drawable-nodpi")

    from(padded)
    into(targetDir)

    onlyIf { padded.exists() }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("copyAppIcon")
    dependsOn(rootProject.tasks.named("splitPadBaseModelChunks"))
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.google.android.material:material:1.13.0")

    implementation(project(":feature-chatbot"))
    implementation(project(":gyango-core"))
    implementation(project(":gyango-litert-inference"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.android.play:asset-delivery-ktx:2.2.2")
}