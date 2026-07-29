plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.edu.hut.course"
    compileSdk = 35

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

// Android Studio 需要 testClasses 任务，但 AGP 不自动生成（项目暂无单测）
tasks.register("testClasses") {
    group = "verification"
    description = "Placeholder for Android Studio test runner compatibility"
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.recyclerview)
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("com.theokanning.openai-gpt3-java:service:0.18.2")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Flutter module（引擎JAR已缓存在本地Gradle，正常解析即可）
    implementation("cn.edu.hut.course.flutter:flutter_debug:1.0")
}