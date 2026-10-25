plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.ricox.boostyvp.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Fuzz tests replay their saved inputs in every run; JAZZER_FUZZ=1 fuzzes (see docs/DEVELOPMENT.md).
    testOptions { unitTests.all { it.useJUnitPlatform() } }
}

kotlin {
    compilerOptions {
        // Emit Java 11 bytecode. The Gradle daemon itself still runs on JDK 17+
        // because AGP 8 requires it -- this only sets the output class version.
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:localization"))
    implementation(project(":core:domain"))

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    // Fuzz tests (Jazzer) run on JUnit 5; the existing JUnit 4 tests run on its vintage engine.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jazzer.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
