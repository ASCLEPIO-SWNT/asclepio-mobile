import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// API_BASE_URL desde local.properties (no versionado). Por defecto: localhost con `adb reverse tcp:3000 tcp:3000`
// (Android 17 / targetSdk 37 bloquea la red local, como 10.0.2.2, sin ACCESS_LOCAL_NETWORK).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val apiBaseUrl: String = localProps.getProperty("API_BASE_URL") ?: "http://localhost:3000"

android {
    namespace = "co.asclepio.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "co.asclepio.mobile"
        minSdk = 31 // docs/adr/0001-minsdk-31.md
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        // Versión del contrato consumido (feature_list.json → consumes_contract).
        buildConfigField("String", "CONSUMED_CONTRACT", "\"0.11.0\"")
        // F-11-mobile (D2): Gemma 4 E2B-it de litert-community en HF, fijado a una revisión y a su SHA-256 LFS.
        // El modelo no va en el APK: se descarga opcionalmente por Wi-Fi (ModelDownloadWorker).
        buildConfigField(
            "String",
            "MODEL_URL",
            "\"https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/2d74ac41a33d7da82f39f7d5720a04c68514a103/gemma-4-E2B-it.litertlm\"",
        )
        buildConfigField("long", "MODEL_BYTES", "2588147712L")
        buildConfigField("String", "MODEL_SHA256", "\"181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c\"")
        buildConfigField("String", "MODEL_ID", "\"gemma-4-E2B-it.litertlm@2d74ac4\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // F-11-mobile R19: la evaluación manual (EvalInstrumentedTest) lee los casos de docs/eval/ (solo en el APK de prueba).
    sourceSets["androidTest"].assets.srcDir("../docs/eval")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    // F-10-mobile (specs/F-10-mobile/design.md, D1): SSE de la posición en la fila, misma versión que OkHttp.
    implementation(libs.okhttp.sse)

    // F-11-mobile (specs/F-11-mobile/design.md, D1 y D3): motor local y descarga del modelo en primer plano.
    implementation(libs.litertlm.android)
    implementation(libs.androidx.work.runtime.ktx)

    // F-01-mobile (specs/F-01-mobile/design.md, "Dependencias nuevas")
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Android 37: evita InputManager.getInstance por reflexión del Espresso 3.5 transitivo de Compose.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.okhttp.mockwebserver)
    debugImplementation(libs.compose.ui.test.manifest)
}
