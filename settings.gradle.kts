pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Xposed / LSPosed API
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "Xposed-Mpesa-parser"
include(":app")
