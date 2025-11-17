plugins {
    id("ivy.kotlin-android")
    id("ivy.hilt")
}

android {
    namespace = "com.ivy.data.auth"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.hilt)
    implementation(libs.firebase.auth)
    implementation(libs.kotlin.coroutines.googleplay.temp)
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    testImplementation(libs.bundles.testing)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.coroutines.test)
}
