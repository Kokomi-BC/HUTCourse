pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("flutter_ui_module/build/host/outputs/repo") }
        maven { url = uri("https://storage.flutter-io.cn/download.flutter.io") }
    }
}

rootProject.name = "My Application"
include(":app")

// Flutter模块（取消注释以启用Flutter UI）
// include(":flutter_ui_module")
