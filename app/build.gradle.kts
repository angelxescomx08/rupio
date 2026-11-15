import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// URLs públicas de Neon (NO son secretos). Se leen de local.properties o variables de entorno.
// La contraseña de la base de datos NUNCA va en la app.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun cfg(key: String): String = (localProps.getProperty(key) ?: System.getenv(key) ?: "").trim()

android {
    namespace = "com.rupio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rupio.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.3"
        // URLs públicas del proyecto Neon "Rupio" (no son secretos). Se pueden sobreescribir en local.properties.
        val authUrl = cfg("NEON_AUTH_URL").ifBlank { "https://ep-wild-art-bdlip6s4.neonauth.c-8.us-east-2.aws.neon.tech/neondb/auth" }
        val dataUrl = cfg("NEON_DATA_API_URL").ifBlank { "https://ep-wild-art-bdlip6s4.apirest.c-8.us-east-2.aws.neon.tech/neondb/rest/v1" }
        buildConfigField("String", "NEON_AUTH_URL", "\"$authUrl\"")
        buildConfigField("String", "NEON_DATA_API_URL", "\"$dataUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        // Capturas de pantalla (Roborazzi): se graban en app/screenshots/ al correr las pruebas.
        unitTests.all { it.systemProperty("roborazzi.test.record", "true"); it.maxHeapSize = "3g" }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
