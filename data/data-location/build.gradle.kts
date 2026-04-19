plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.handleit.transit.data.location"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-common"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.play.services.location)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play.services)
    implementation(libs.timber)
}
