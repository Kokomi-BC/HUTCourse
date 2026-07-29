plugins {
    id("com.android.application") version "9.1.1"
    // AGP 9.0+ 已内置 Kotlin 支持，不再需要 kotlin-android 插件
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

android {
    namespace = "cn.edu.hut.course"
    compileSdk = 37

    defaultConfig {
        applicationId = "cn.edu.hut.course"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "2.0"
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("com.theokanning.openai-gpt3-java:service:0.18.2")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // JetBrains Compose 运行时（backdrop 库依赖 org.jetbrains.compose.*）
    implementation("org.jetbrains.compose.runtime:runtime:1.8.0")
    implementation("org.jetbrains.compose.foundation:foundation:1.8.0")
    implementation("org.jetbrains.compose.ui:ui:1.8.0")
    implementation("org.jetbrains.compose.material3:material3:1.8.0")

    // Material Icons（使用 AndroidX 坐标，core 图标已足够）
    implementation("androidx.compose.material:material-icons-core:1.7.6")

    // AndroidX Activity Compose（ComposeView 支持）
    implementation(libs.activity.compose)

    // Lifecycle + SavedState — ComposeView 在 PlatformView 中需要
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // Android Liquid Glass (backdrop) Maven 依赖
    implementation(libs.backdrop)

    // Flutter module
    implementation("cn.edu.hut.course.flutter:flutter_debug:1.0")
}
