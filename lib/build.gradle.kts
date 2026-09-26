plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // Only KSP — the Hilt Gradle plugin itself is only needed in the application module.
    // Library modules just need the KSP annotation processor so @Inject classes get their
    // generated _Factory.java, letting the app-module aggregation wire them across the
    // module boundary.
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.llama"
    compileSdk = 35
    ndkVersion = "26.1.10909125"

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17", "-fexceptions", "-frtti")
                arguments(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DBUILD_SHARED_LIBS=ON",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_BUILD_COMMON=ON",
                    "-DLLAMA_OPENSSL=OFF",
                    "-DGGML_NATIVE=OFF",
                    // Link CPU backend into libggml.so so inference works without
                    // separate libggml-cpu-*.so files in the APK (those were missing).
                    "-DGGML_BACKEND_DL=OFF",
                    "-DGGML_LLAMAFILE=OFF"
                )
                abiFilters("arm64-v8a")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("javax.inject:javax.inject:1")
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
