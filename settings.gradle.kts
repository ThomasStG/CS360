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
        plugins {
            id("com.android.application") version "7.0.4" apply false
            id("org.jetbrains.kotlin.android") version "1.5.31" apply false
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven {setUrl("https://jitpack.io")}

        //jcenter()
    }
    versionCatalogs{create("libs"){from(files("libs.versions.toml"))} }
}

rootProject.name = "SNAR"
include(":app")
 