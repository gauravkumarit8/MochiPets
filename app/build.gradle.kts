import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

// AdMob ids. Debug builds, and any release build without real ids, use Google's TEST ids.
// Real ids are passed with -PadmobAppId=... -PadmobRewardedUnitId=... (GitHub secrets in CI).
val testAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewardedId = "ca-app-pub-3940256099942544/5224354917"
fun optionalProp(name: String): String? =
    (project.findProperty(name) as String?)?.trim()?.takeIf { it.isNotEmpty() }
val releaseAppId: String = optionalProp("admobAppId") ?: testAppId
val releaseRewardedId: String = optionalProp("admobRewardedUnitId") ?: testRewardedId
val testDeviceIds: String = optionalProp("admobTestDevices") ?: ""

android {
    namespace = "com.mochipets.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mochipets.app"
        minSdk = 26
        targetSdk = 36
        versionCode = maxOf(buildNumber, 1)
        versionName = "$versionBase.$buildNumber"
        buildConfigField("String", "TEST_DEVICE_IDS", "\"$testDeviceIds\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // Release signing uses environment variables (set from GitHub secrets in CI).
    // Without them the release bundle is built unsigned.
    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (!path.isNullOrBlank() && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["admobAppId"] = testAppId
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"$testRewardedId\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = releaseAppId
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"$releaseRewardedId\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
}
