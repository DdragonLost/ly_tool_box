plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.beianlove.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.beianlove.assistant"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = "yanjing@528998"
            keyAlias = "tiktokassistant"
            keyPassword = "yanjing@528998"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = true
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            isZipAlignEnabled = true
            
            // 自定义 APK 文件名格式：assistant_versionName.apk
            applicationVariants.all {
                outputs.forEach { output ->
                    val outputImpl = output as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                    outputImpl.outputFileName = "assistant_${versionName}.apk"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}