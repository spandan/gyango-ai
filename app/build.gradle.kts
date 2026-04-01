plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ai.pocket"
    compileSdk = 34

    defaultConfig {
        applicationId = "ai.pocket"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    androidResources {
        // GGML / GGUF weights: store uncompressed in APK; faster first copy + mmap-friendly.
        noCompress += listOf("bin", "gguf")
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*/.DS_Store"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
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
            (this as com.android.build.gradle.internal.api.ApkVariantOutputImpl).outputFileName =
                "pocketai-${variantName}.apk"
        }
    }
}

// Resize pocketAIIcon.webp to fit adaptive icon safe zone (66dp)
val resizeAppIcon = tasks.register<Exec>("resizeAppIcon") {
    val iconAsset = file("src/main/assets/images/pocketAIIcon.webp")
    val outDir = layout.buildDirectory.dir("generated/icon").get().asFile
    val resized = outDir.resolve("pocket_ai_resized.png")

    inputs.file(iconAsset)
    outputs.file(resized)

    commandLine("sips", "-Z", "264", "-s", "format", "png", iconAsset.absolutePath, "-o", resized.absolutePath)

    onlyIf { iconAsset.exists() }
    doFirst { outDir.mkdirs() }
}

val padAppIcon = tasks.register<Exec>("padAppIcon") {
    dependsOn(resizeAppIcon)
    val outDir = layout.buildDirectory.dir("generated/icon").get().asFile
    val resized = outDir.resolve("pocket_ai_resized.png")
    val padded = outDir.resolve("pocket_ai.png")

    inputs.file(resized)
    outputs.file(padded)

    commandLine("sips", "-p", "432", "432", "--padColor", "1565C0", resized.absolutePath, "-o", padded.absolutePath)

    onlyIf { resized.exists() }
}

tasks.register<Copy>("copyAppIcon") {
    dependsOn(padAppIcon)
    val outDir = layout.buildDirectory.dir("generated/icon").get().asFile
    val padded = outDir.resolve("pocket_ai.png")
    val targetDir = file("src/main/res/drawable-nodpi")

    from(padded)
    into(targetDir)

    onlyIf { padded.exists() }
}

tasks.named("preBuild") {
    dependsOn("copyAppIcon")
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
    implementation(project(":pocket-whisper"))
    implementation(project(":pocket-api"))
    implementation(project(":pocket-core"))
    implementation(project(":pocket-models-llm"))
    implementation(project(":pocket-orchestration"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
