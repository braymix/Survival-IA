plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.survivalwiki.core.llm"
    compileSdk = 35
    // Usato solo quando si compila con -PwithLlama.
    ndkVersion = "26.3.11579264"

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // La compilazione della libreria nativa llama.cpp è opt-in: richiede NDK + il submodule
        // (git submodule update --init native/llama.cpp). Senza il flag, il modulo compila solo
        // Kotlin/JVM e l'app resta in modalità "solo estratti". Attiva con: ./gradlew ... -PwithLlama
        if (project.hasProperty("withLlama")) {
            ndk { abiFilters += "arm64-v8a" }
            externalNativeBuild {
                cmake {
                    cppFlags += "-std=c++17"
                    arguments += listOf("-DANDROID_STL=c++_shared")
                }
            }
        }
    }

    if (project.hasProperty("withLlama")) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
