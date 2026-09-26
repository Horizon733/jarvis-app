plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.ai_agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ai_agent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "none"
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirsts += listOf(
                "**/libonnxruntime.so",
                "**/libonnxruntime4j_jni.so",
            )
        }
    }
    androidResources {
        noCompress += listOf("onnx", "tflite")
    }
}

// Persist Room's schema JSON so migrations become reviewable in code review.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.configureEach {
    exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Navigation
    implementation(libs.androidx.navigation.compose)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.okhttp)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Lifecycle (process-wide observer for foreground/background)
    implementation(libs.androidx.lifecycle.process)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    
    // Markdown
    implementation(libs.compose.markdown)

    implementation(project(":lib"))

    // Voice: ONNX Runtime 1.28.2 matches sherpa-onnx 1.13.8 (see scripts/download_onnxruntime.ps1).
    implementation(files("libs/onnxruntime-android-1.28.2.aar"))
    implementation("xyz.rementia:openwakeword:0.1.5") {
        exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
    }
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:1.13.8")
    implementation("org.apache.commons:commons-compress:1.26.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
