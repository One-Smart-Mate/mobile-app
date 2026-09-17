import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":sharedLogic"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.work.runtime)
    implementation(libs.koin.android.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use(::load)
}

fun localConfiguration(name: String): String =
    localProperties.getProperty(name)?.trim()
        ?: System.getenv(name)?.trim()
        ?: ""

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val developmentApiBaseUrl = localConfiguration("OSM_DEV_API_BASE_URL")
val productionApiBaseUrl = localConfiguration("OSM_PROD_API_BASE_URL")

require(developmentApiBaseUrl.isNotBlank()) {
    "Missing OSM_DEV_API_BASE_URL in local.properties or the environment."
}
require(productionApiBaseUrl.isNotBlank()) {
    "Missing OSM_PROD_API_BASE_URL in local.properties or the environment."
}

val releaseStoreFile = localConfiguration("OSM_KEYSTORE_FILE")
val releaseStorePassword = localConfiguration("OSM_KEYSTORE_PASSWORD")
val releaseKeyAlias = localConfiguration("OSM_KEY_ALIAS")
val releaseKeyPassword = localConfiguration("OSM_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all(String::isNotBlank)

android {
    namespace = "com.ih.osm"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ih.osm"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", developmentApiBaseUrl.asBuildConfigString())
            buildConfigField("String", "APP_ENVIRONMENT", "dev".asBuildConfigString())
            buildConfigField("boolean", "ENABLE_NETWORK_LOGGING", "true")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", productionApiBaseUrl.asBuildConfigString())
            buildConfigField("String", "APP_ENVIRONMENT", "prod".asBuildConfigString())
            buildConfigField("boolean", "ENABLE_NETWORK_LOGGING", "false")
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
