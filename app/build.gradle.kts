plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.hehpollon.drivemood"
    compileSdk = 36
    useLibrary("android.car", false)

    defaultConfig {
        applicationId = "io.github.hehpollon.drivemood"
        minSdk = 28
        targetSdk = 34
        versionCode = 12
        versionName = "0.4.6"
        testInstrumentationRunner = "io.github.hehpollon.drivemood.verification.RuntimeChecks"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("demo") {
            dimension = "environment"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
        create("pleos") {
            dimension = "environment"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    "pleosImplementation"(libs.pleos.vehicle)
    "pleosImplementation"(libs.pleos.navihelper)
    "pleosImplementation"(libs.pleos.tts)
    testImplementation("junit:junit:4.13.2")
}
