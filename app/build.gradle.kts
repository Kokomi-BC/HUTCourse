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
        versionName = "1.0"
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
            vendor.set(JvmVendorSpec.matching("Oracle"))
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("com.theokanning.openai-gpt3-java:service:0.18.2")
    implementation("io.noties.markwon:core:4.6.2")
}