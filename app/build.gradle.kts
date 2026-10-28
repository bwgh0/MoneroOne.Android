plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

// Username registry values (public). Release builds always get production. Debug builds get staging,
// and a Gradle property of the same name (for example usernameRegistryUrl) overrides each one, for a
// registry on this machine. Checked on 2026-10-28 against each registry's /v1/info and its mirror's epoch 0.
val usernameProduction = mapOf(
    "usernameRegistryUrl" to "https://registry.monero.one",
    "usernameMirrorUrl" to "https://mirror.monero.one/oah/v1/",
    "usernameGenesisRoot" to "37c80669fb410913857b678d9aea9a54c5344fe152c0fb247549040d05477f26",
    "usernameRegistrarKey" to "40f69d3b401370c38c6f0b62976a688fd44b56abe22be66168b1f9a44822a988",
    "usernameRulesHash" to "b775ea7a655fd01c57820f09d08a421e51b0739240fbd940f399e65bd6fcdeac",
    // Placeholder until the terms text exists: replace it before a release build ships.
    "usernameTermsVersion" to "terms-pending-v1",
)
val usernameStaging = mapOf(
    "usernameRegistryUrl" to "https://staging-registry.monero.one",
    "usernameMirrorUrl" to "https://staging-mirror.monero.one/oah/v1/",
    "usernameGenesisRoot" to "f0f5024f667e6957ce56d0a746a358b5bae36f7772dd56a0dd489a66a85e815e",
    "usernameRegistrarKey" to "809d48344049bab6e37503094af5b3a947184ee0be45b304305d7c916d1588e2",
    "usernameRulesHash" to "b775ea7a655fd01c57820f09d08a421e51b0739240fbd940f399e65bd6fcdeac",
    "usernameTermsVersion" to "local-development-v1",
)
val usernameFields = mapOf(
    "usernameRegistryUrl" to "USERNAME_REGISTRY_URL",
    "usernameMirrorUrl" to "USERNAME_MIRROR_URL",
    "usernameGenesisRoot" to "USERNAME_GENESIS_ROOT",
    "usernameRegistrarKey" to "USERNAME_REGISTRAR_KEY",
    "usernameRulesHash" to "USERNAME_RULES_HASH",
    "usernameTermsVersion" to "USERNAME_TERMS_VERSION",
)

fun buildConfigString(value: String): String = "\"" + value
    .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""

fun usernameSetting(name: String): String =
    buildConfigString(providers.gradleProperty(name).orElse(usernameStaging.getValue(name)).get())

// The Google Cloud project number for Play Integrity, from the Gradle property or the environment
// variable PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER. Empty by default: a release build then claims no
// username, and a debug build sends the development device proof.
fun playIntegrityCloudProjectNumber(): String {
    val name = "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER"
    val value = providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orElse("").get().trim()
    require(value.all { it in '0'..'9' }) { "$name must hold digits only" }
    return "\"" + value + "\""
}

android {
    namespace = "one.monero.moneroone"
    compileSdk = 36

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "one.monero.moneroone"
        minSdk = 27
        targetSdk = 36
        versionCode = 19
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", playIntegrityCloudProjectNumber())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            usernameFields.forEach { (name, field) ->
                buildConfigField("String", field, buildConfigString(usernameProduction.getValue(name)))
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            usernameFields.forEach { (name, field) -> buildConfigField("String", field, usernameSetting(name)) }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // MoneroKit
    implementation(project(":monerokit"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Security
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // Image Loading
    implementation(libs.coil.compose)

    // QR Code generation
    implementation(libs.zxing.android.embedded)
    implementation(libs.qrcode.kotlin)

    // CameraX + ZXing (for QR scanning)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation("com.google.guava:guava:32.1.3-android")

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Glance (App Widgets) - kept for WorkManager session cleanup; widgets use RemoteViews directly
    // implementation(libs.androidx.glance.appwidget)
    // implementation(libs.androidx.glance.material3)

    // Charts
    implementation(libs.vico.compose.m3)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Username claims: the device check (Play Integrity, standard API)
    implementation(libs.play.integrity)

    // Logging
    implementation(libs.timber)
    implementation(libs.netcipher)

    // HD Wallet Kit (for BIP39 mnemonic generation)
    implementation(libs.hd.wallet.kit)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
