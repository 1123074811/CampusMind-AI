plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "cn.campusmind.app"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "cn.campusmind.app"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    val keystorePath = System.getenv("CAMPUSMIND_KEYSTORE_PATH")
    val keystorePassword = System.getenv("CAMPUSMIND_KEYSTORE_PASSWORD")
    val keyAliasName = System.getenv("CAMPUSMIND_KEY_ALIAS")
    val keyPasswordValue = System.getenv("CAMPUSMIND_KEY_PASSWORD")
    if (listOf(keystorePath, keystorePassword, keyAliasName, keyPasswordValue).all { !it.isNullOrBlank() }) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath!!)
            storePassword = keystorePassword
            keyAlias = keyAliasName
            keyPassword = keyPasswordValue
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) } && signingConfig == null) {
                throw GradleException("Release signing requires CAMPUSMIND_KEYSTORE_PATH/PASSWORD, CAMPUSMIND_KEY_ALIAS and CAMPUSMIND_KEY_PASSWORD")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
