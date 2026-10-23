plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.github.ricox.boostyvp.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
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
    implementation(project(":core:domain"))
    implementation(project(":core:network"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.core)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)

    constraints {
        // Hilt also uses saved-state ViewModels; align it with the app's Lifecycle version.
        implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:${libs.versions.lifecycle.get()}")
    }
}
