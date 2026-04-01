import java.util.Properties
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.pocket.models.llm"
    compileSdk = 34

    defaultConfig {
        minSdk = 28
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    packaging {
        jniLibs {
            pickFirsts += "**/libomp.so"
            useLegacyPackaging = true
        }
    }
}

// Copy libomp.so from NDK. Uses ndk.dir or sdk.dir from local.properties.
val localProps = Properties()
rootProject.file("local.properties").takeIf { f -> f.exists() }?.reader()?.use { r -> localProps.load(r) }
val ndkDir = localProps.getProperty("ndk.dir") ?: localProps.getProperty("sdk.dir")?.plus("/ndk/26.1.10909125")
tasks.register<Copy>("copyLibOmp") {
    val hostTag = if (System.getProperty("os.name").lowercase().contains("mac")) "darwin-x86_64" else "linux-x86_64"
    val ompPath = ndkDir?.let { d -> "$d/toolchains/llvm/prebuilt/$hostTag/lib/clang/17/lib/linux/aarch64/libomp.so" }
    val ompFile = ompPath?.let { p -> file(p) }
    onlyIf { ompFile?.exists() == true }
    from(if (ompFile != null) ompFile else layout.projectDirectory.dir("src/main/jniLibs/arm64-v8a"))
    into("src/main/jniLibs/arm64-v8a")
}
tasks.named("preBuild") { dependsOn("copyLibOmp") }

dependencies {
    implementation(project(":pocket-core"))
    implementation(project(":pocket-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
