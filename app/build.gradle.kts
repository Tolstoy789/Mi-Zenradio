plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zenradio.app"
    compileSdk = 35

    // En GitHub Actions cada compilación sube el número de versión, así la APK
    // nueva siempre se instala como actualización de la anterior.
    val buildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()

    defaultConfig {
        applicationId = "com.zenradio.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber ?: 1
        versionName = "0.1.${buildNumber ?: 0}"
    }

    signingConfigs {
        // Clave de pruebas incluida en el proyecto para que todas las APK
        // (las de GitHub y las de Android Studio) se instalen una encima de
        // otra. Para publicar en Google Play hay que crear una clave propia.
        create("zen") {
            storeFile = file("zenradio-dev.keystore")
            storePassword = "zenradio"
            keyAlias = "zenradio"
            keyPassword = "zenradio"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("zen")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("zen")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX base
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Media3: ExoPlayer + MediaSession (notificación y controles en segundo plano)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
}
