plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.definitecoding.bydadasrevive"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.definitecoding.bydadasrevive"
        minSdk = 26
        targetSdk = 32
        // A release is cut from a tag: the tag names it, and the CI run number keeps
        // versionCode climbing so the car offers an update rather than a reinstall.
        // A build from anywhere else is marked dev so it cannot be mistaken for one.
        versionCode = System.getenv("RELEASE_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("RELEASE_NAME") ?: "1.0-dev"
    }

    // Release builds are signed in CI from base64 secrets; a local build without
    // them falls through to the debug key so the project still assembles.
    val keystorePath = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASS")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASS") ?: System.getenv("KEYSTORE_PASS")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Robolectric renders the real layout on the JVM, so it needs the real resources.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Roborazzi writes a file only in record mode. Setting it here rather than
            // on the CI command line means a local run produces the same screenshots.
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    lint {
        // Sideloaded onto a car head unit, never published to Play, so the Play
        // target-API floor does not apply. targetSdk stays at the car's own level.
        disable += "ExpiredTargetSdkVersion"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Layout regression tests. These run on the JVM against the car's exact screen
    // configuration, so a clipped control fails the build instead of reaching a car.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.1")
    // createComposeRule needs an activity to host the composition under test.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
