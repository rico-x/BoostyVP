import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val releaseVersion = Properties().apply {
    load(StringReader(providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText.get()))
}

val prepareLegalAssets by tasks.registering(Sync::class) {
    from(listOf(rootProject.file("LICENSE.md"), rootProject.file("NOTICE.md"), rootProject.file("DISCLAIMER.md"),
        rootProject.file("SECURITY.md"))) { into("legal") }
    from(listOf(rootProject.file("docs/THIRD_PARTY.md"), rootProject.file("docs/PRIVACY.md"))) { into("legal/docs") }
    from(listOf(rootProject.file("LICENSES/MIT.txt"), rootProject.file("LICENSES/Apache-2.0.txt"))) { into("legal/LICENSES") }
    into(layout.buildDirectory.dir("generated/legalAssets"))
}

tasks.named("preBuild") { dependsOn(prepareLegalAssets) }

android {
    namespace = "io.github.ricox.boostyvp"
    compileSdk = 35

    defaultConfig {
        // Independent fork identity. Never reuse another application's account data.
        applicationId = "io.github.ricox.boostyvp"
        minSdk = 23
        targetSdk = 35
        versionCode = releaseVersion.getProperty("versionCode").toInt()
        versionName = releaseVersion.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Opt in for distribution; ordinary debug/device-test builds stay universal.
    splits {
        abi {
            isEnable = providers.gradleProperty("splitApks").orNull == "true"
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/legalAssets"))

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:auth"))
    implementation(project(":core:network"))
    implementation(project(":feature:pairing"))
    implementation(project(":feature:home"))
    implementation(project(":feature:blog"))
    implementation(project(":feature:player"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.tv.material)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.media3.ui)
    androidTestImplementation(libs.androidx.media3.common)
    androidTestImplementation(libs.androidx.paging.compose)
    androidTestImplementation(libs.okhttp.core)
    androidTestImplementation(libs.retrofit.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
