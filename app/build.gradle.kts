plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.apollo)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.canim.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.canim.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 52
        versionName = "v6.4.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = project.findProperty("CANIM_KEYSTORE_FILE") as? String
                ?: System.getenv("CANIM_KEYSTORE_FILE")
            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storePassword = project.findProperty("CANIM_KEYSTORE_PASSWORD") as? String
                    ?: System.getenv("CANIM_KEYSTORE_PASSWORD") ?: ""
                keyAlias = project.findProperty("CANIM_KEY_ALIAS") as? String
                    ?: System.getenv("CANIM_KEY_ALIAS") ?: ""
                keyPassword = project.findProperty("CANIM_KEY_PASSWORD") as? String
                    ?: System.getenv("CANIM_KEY_PASSWORD") ?: ""
            }
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (output != null) {
                val fileName = if (variant.buildType.name == "release") {
                    "canim-universal-release-${variant.versionName}.apk"
                } else {
                    "canim-debug-${variant.versionName}.apk"
                }
                output.outputFileName = fileName
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val keystorePath = project.findProperty("CANIM_KEYSTORE_FILE") as? String
                ?: System.getenv("CANIM_KEYSTORE_FILE")
            signingConfig = if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Coil for Compose Image Loading
    implementation(libs.coil.compose)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // AndroidX Security Crypto (Secure EncryptedSharedPreferences)
    implementation(libs.androidx.security.crypto)

    // Apollo Kotlin 4.x Runtime
    implementation(libs.apollo.runtime)

    // Dagger Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Unit & Robolectric testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation(libs.kotlinx.coroutines.test)
}

apollo {
    service("anilist") {
        packageName.set("com.canim.app.data.remote.anilist.graphql")
        mapScalar("FuzzyDateInt", "kotlin.Int")
    }
}
