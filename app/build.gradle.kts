plugins {
    alias(libs.plugins.android.application)
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    namespace = "com.example.truenorth"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.truenorth"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ADD THIS - BuildConfig field for API key
        buildConfigField("String", "PLACES_API_KEY", "\"${getApiKey()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Our extra dependencies
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.libraries.places:places:3.5.0")

    // CRITICAL FIX - Enables java.time.Duration on older Android versions
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

fun getApiKey(): String {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        val lines = propertiesFile.readLines()
        for (line in lines) {
            if (line.startsWith("PLACES_API_KEY=")) {
                return line.substringAfter("=").trim()
            }
        }
    }
    return "DEFAULT_API_KEY"
}