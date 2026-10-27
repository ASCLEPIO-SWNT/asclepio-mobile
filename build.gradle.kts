// AGP 9: Kotlin integrado (no se aplica org.jetbrains.kotlin.android).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
