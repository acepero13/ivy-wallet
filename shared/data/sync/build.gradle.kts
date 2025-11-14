plugins {
    id("ivy.kotlin-android")
    id("ivy.hilt")
}

android {
    namespace = "com.ivy.data.sync"
}

dependencies {
    implementation(projects.shared.base)
    implementation(projects.shared.data.core)

    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.hilt)

    // Firebase
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)

    // Logging
    implementation(libs.timber)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.coroutines.test)
}
