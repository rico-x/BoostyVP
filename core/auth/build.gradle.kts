import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Keep emulator LAN overrides in the ignored local.properties so Android
// Studio's Run action uses them too. Explicit -P arguments take precedence.
val localPairing = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }
        ?.inputStream()?.use { load(it) }
}
val pairingHost = providers.gradleProperty("pairingHost")
    .orElse(localPairing.getProperty("pairingHost", "")).get()
require(pairingHost.isEmpty() || pairingHost.split('.').let { parts ->
    parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }
}) { "pairingHost must be an IPv4 address" }
val pairingPort = providers.gradleProperty("pairingPort")
    .orElse(localPairing.getProperty("pairingPort", "8765")).get().toInt()
require(pairingPort in 0..65535) { "pairingPort must be 0 (automatic) or a TCP port" }

android {
    namespace = "io.github.ricox.boostyvp.core.auth"
    compileSdk = 36

    defaultConfig {
        buildConfigField("String", "PAIRING_HOST", "\"\"")
        buildConfigField("int", "PAIRING_PORT", "8765")
        minSdk = 23
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { buildConfig = true }

    // Fuzz tests replay their saved inputs in every run; JAZZER_FUZZ=1 fuzzes (see docs/DEVELOPMENT.md).
    testOptions { unitTests.all { it.useJUnitPlatform() } }

    buildTypes {
        debug {
            buildConfigField("String", "PAIRING_HOST", "\"$pairingHost\"")
            buildConfigField("int", "PAIRING_PORT", pairingPort.toString())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    // Fuzz tests (Jazzer) run on JUnit 5; the existing JUnit 4 tests run on its vintage engine.
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jazzer.junit)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
