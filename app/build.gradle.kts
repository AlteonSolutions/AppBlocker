plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

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

    // One shared signing key for every build: local debug, CI artifacts and GitHub releases. Android
    // refuses to update an app signed by a different key, and the only way past that is an uninstall,
    // which wipes the PIN and every rule. Without this, each machine and each CI runner signs with its
    // own generated debug key, and builds from different places can't install over each other.
    // The key and password are committed on purpose: the app is sideloaded onto the family's own
    // tablets and the repo is private (see DECISIONS.md). Making the repo public means a new key.
    signingConfigs {
        getByName("debug") {
            storeFile = file("signing/appgate.keystore")
            storePassword = "appgate"
            keyAlias = "appgate"
            keyPassword = "appgate"
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
            // Same shared key as debug builds, so a release installs over a debug build and vice versa.
            signingConfig = signingConfigs.getByName("debug")
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
