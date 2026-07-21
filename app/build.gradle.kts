import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Signing credentials live in local.properties (gitignored) or environment
// variables (for CI) — never in this file, which is public.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String? =
    localProperties.getProperty(name) ?: System.getenv(name)

android {
    namespace = "com.trencadis.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.trencadis.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 4
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = rootProject.file("trencadis-release.keystore")
            val storePass = secret("KEYSTORE_PASSWORD")
            val keyPass = secret("KEY_PASSWORD")
            if (keystoreFile.exists() && storePass != null && keyPass != null) {
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = secret("KEY_ALIAS") ?: "trencadis"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true  // Enable R8 code shrinking
            isShrinkResources = true  // Remove unused resources
            // Only sign when credentials are available; otherwise the release
            // build produces an unsigned artifact instead of failing to configure.
            signingConfig = signingConfigs.getByName("release")
                .takeIf { it.storeFile?.exists() == true }
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    
    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    
    // libpd for Pure Data audio synthesis
    implementation(libs.pd.core)
    
    // ViewModel Compose
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Permissions
    implementation(libs.accompanist.permissions)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}