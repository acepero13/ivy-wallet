plugins {
    id("ivy.kotlin-android")
    id("ivy.hilt")
    org.jetbrains.kotlin.plugin.compose
}

android {
    namespace = "com.ivy.data.auth"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)
    implementation(projects.shared.ui.core)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.hilt)
    implementation(libs.bundles.compose)
    implementation(libs.firebase.auth)
    implementation(libs.kotlin.coroutines.googleplay.temp)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.coroutines.test)
}
