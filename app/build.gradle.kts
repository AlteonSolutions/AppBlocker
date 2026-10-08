plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Stable release signing key, passed in by .github/workflows/release.yml from repository secrets.
// Android refuses to update an app signed by a different key, and the only way past that is an
// uninstall, which wipes the PIN and every rule. So every APK published to GitHub Releases must be
// signed by this one key. Without these variables (any local build), release falls back to the
// per-machine debug key, which is fine for a test install and never published.
val releaseKeystore: String? = providers.environmentVariable("APPGATE_KEYSTORE_FILE").orNull

fun requiredEnv(name: String): String = providers.environmentVariable(name).orNull
    ?: throw GradleException("$name must be set when APPGATE_KEYSTORE_FILE is. See SETUP.md.")

android {
    namespace = "com.alteon.appgate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alteon.appgate"
        // Fire OS 6 is API 25; Fire OS 7 is API 28; Fire OS 8 is API 30.
        minSdk = 25
        // 34 avoids Android 15's forced edge-to-edge layout on newer Go tablets.
        targetSdk = 34
        // The release workflow derives both from the git tag (v1.2.3 -> 10203, "1.2.3"). Android only
        // accepts an update with a higher versionCode, so it must rise with every published release.
        versionCode = (findProperty("appgate.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("appgate.versionName") as String?) ?: "0.1.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = requiredEnv("APPGATE_KEYSTORE_PASSWORD")
                keyAlias = requiredEnv("APPGATE_KEY_ALIAS")
                keyPassword = requiredEnv("APPGATE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
