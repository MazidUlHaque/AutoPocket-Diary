plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Signing details come from GitHub Actions (see .github/workflows/build-apk.yml), never from the repo.
val signingKeystore: String? = System.getenv("SIGNING_KEYSTORE")
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")
val signingCertSha256: String = System.getenv("SIGNING_CERT_SHA256") ?: ""
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.mazidulhaque.autopocket"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mazidulhaque.autopocket"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // The app compares its own signature with this to spot repackaged copies.
        buildConfigField("String", "SIGNING_CERT_SHA256", "\"$signingCertSha256\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // Two editions from the same code:
    //  - standard: no SMS permission, installs normally; SMS are shared, pasted or imported from a file.
    //  - auto: reads the SMS inbox itself (READ_SMS). Play Protect blocks this from browser downloads
    //    in some countries, so it's for people who choose to allow it.
    flavorDimensions += "edition"
    productFlavors {
        create("standard") {
            dimension = "edition"
            applicationIdSuffix = ".standard"
            resValue("string", "app_name", "AutoPocket Diary")
            buildConfigField("boolean", "AUTO_SMS", "false")
        }
        create("auto") {
            dimension = "edition"
            resValue("string", "app_name", "AutoPocket Diary Auto")
            buildConfigField("boolean", "AUTO_SMS", "true")
        }
    }

    signingConfigs {
        if (signingKeystore != null && signingPassword != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = signingPassword
                keyAlias = "autopocket"
                keyPassword = signingPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
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
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Encrypts the database file on the phone.
    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")

    testImplementation("junit:junit:4.13.2")
}
