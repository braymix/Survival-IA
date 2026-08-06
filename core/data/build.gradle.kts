plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.survivalwiki.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:retrieval"))
    implementation(libs.kotlinx.coroutines.core)

    // Room per cronologia/salvati/note utente.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Corpus reader su SQLite (sqlite-vec + FTS5). Bundled driver espone addExtension.
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.bundled)

    testImplementation(libs.junit)
}
