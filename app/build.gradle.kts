import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing for the public "Spooky" Play build lives in a gitignored
// keystore.properties (see .gitignore). Absent (fresh clone / CI without the
// secret) → release falls back to debug signing so the build still succeeds.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "net.amiantos.lurker"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // 33 (Android 13) so the app installs on e-ink Android devices, which lag
        // the mainline API level — a Boox Palma is API 33. Nothing here needs 34.
        minSdk = 33
        targetSdk = 36
        versionCode = 25
        versionName = "0.9.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ONE codebase, two identities. Fork-only features (custom aliases, DCC
    // send/chat, fserve) are gated at RUNTIME on the connected server's
    // capabilities (LurkerClient.serverExtended), so BOTH flavors carry all the
    // code and simply light those surfaces up on a fork server and hide them on
    // stock/hosted Lurker — no stripped branch to maintain. Flavor-specific name
    // + launcher icon live in src/<flavor>/res.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            // Private daily-driver "Lurker" — name + blue icon come from src/main.
            applicationId = "net.amiantos.lurker"
        }
        create("spooky") {
            dimension = "distribution"
            // Public Play "Spooky for Lurker" — name + green icon in src/spooky.
            applicationId = "chat.irc.lurker"
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Only the spooky flavor's release ships to Play; debug fallback keeps
            // fresh clones building.
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
