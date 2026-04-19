plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.handleit.transit.feature.map"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:core-model"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-fsm"))
    implementation(project(":data:data-gtfs"))
    implementation(project(":data:data-gtfsrt"))
    implementation(project(":data:data-location"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.navigation)

    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)

    // OSMDroid for OpenStreetMap support
    implementation("org.osmdroid:osmdroid-android:6.1.17")

    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.coroutines.android)
    implementation(libs.timber)

    debugImplementation(libs.compose.ui.tooling)
}
