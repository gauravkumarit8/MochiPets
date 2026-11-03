import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version comes from version.properties (base) plus -PbuildNumber=<n> (set by GitHub Actions).
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val versionBase: String = versionProps.getProperty("VERSION_BASE", "0.1")
val buildNumber: Int = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0

android {
    namespace = "com.mochipets.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mochipets.app"
        minSdk = 26
        targetSdk = 34
        versionCode = maxOf(buildNumber, 1)
        versionName = "$versionBase.$buildNumber"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
