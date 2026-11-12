pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // Versiones centralizadas. No se declaran en el build raíz para que AGP y el
        // plugin de Kotlin compartan classloader dentro del módulo :app.
        kotlin("jvm") version "2.0.21"
        kotlin("android") version "2.0.21"
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pagobox"

// Módulos Kotlin puros: se compilan y prueban sin Android SDK.
include(":domain")
include(":payment-engine")

// La app Android solo se incluye cuando hay un Android SDK disponible
// (Android Studio crea local.properties con sdk.dir).
val hasAndroidSdk = file("local.properties").exists() ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk) {
    include(":app")
}
