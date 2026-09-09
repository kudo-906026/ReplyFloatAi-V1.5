import java.util.Base64
import java.util.Properties

val envFile = rootProject.file(".env")
val envProps = Properties()
if (envFile.exists()) {
    envFile.inputStream().use { input -> envProps.load(input) }
}
val rawGemini = envProps.getProperty("GEMINI_API_KEY") ?: System.getenv("GEMINI_API_KEY") ?: ""
val rawOpenAi = envProps.getProperty("OPENAI_API_KEY") ?: System.getenv("OPENAI_API_KEY") ?: ""
val rawAnthropic = envProps.getProperty("ANTHROPIC_API_KEY") ?: System.getenv("ANTHROPIC_API_KEY") ?: ""
val rawGrok = envProps.getProperty("GROK_API_KEY") ?: System.getenv("GROK_API_KEY") ?: ""

val safeGemini = rawGemini.replace("\\", "\\\\").replace("\"", "\\\"")
val safeOpenAi = rawOpenAi.replace("\\", "\\\\").replace("\"", "\\\"")
val safeAnthropic = rawAnthropic.replace("\\", "\\\\").replace("\"", "\\\"")
val safeGrok = rawGrok.replace("\\", "\\\\").replace("\"", "\\\"")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aistudio.replyfloat.kqpzvx"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GEMINI_API_KEY", "\"$safeGemini\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"$safeOpenAi\"")
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"$safeAnthropic\"")
        buildConfigField("String", "GROK_API_KEY", "\"$safeGrok\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            val rootKeystore = rootProject.file("debug.keystore")
            val appKeystore = project.file("debug.keystore")
            val base64KeystoreFile = rootProject.file("debug.keystore.base64")
            val persistentKeystoreBase64 = "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"

            val targetKeystore = when {
                rootKeystore.exists() -> rootKeystore
                appKeystore.exists() -> appKeystore
                else -> {
                    try {
                        val bytes = if (base64KeystoreFile.exists()) {
                            Base64.getDecoder().decode(base64KeystoreFile.readText().trim())
                        } else {
                            Base64.getDecoder().decode(persistentKeystoreBase64)
                        }
                        rootKeystore.writeBytes(bytes)
                        rootKeystore
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            if (targetKeystore != null && targetKeystore.exists()) {
                storeFile = targetKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.json)

    debugImplementation(libs.androidx.ui.tooling)
}
