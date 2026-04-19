plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.handleit.transit.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.handleit.transit"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Maps API key injected from local.properties
        // If key is empty, OpenStreetMap is used as fallback
        val mapsKey = project.findProperty("MAPS_API_KEY")?.toString() ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // All feature and service modules
    implementation(project(":core:core-model"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-fsm"))
    implementation(project(":data:data-gtfs"))
    implementation(project(":data:data-gtfsrt"))
    implementation(project(":data:data-location"))
    implementation(project(":feature:feature-map"))
    implementation(project(":feature:feature-riding"))
    implementation(project(":feature:feature-settings"))
    implementation(project(":service:service-tracking"))

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.appcompat)

    // Coroutines
    implementation(libs.coroutines.android)

    // Logging
    implementation(libs.timber)

    debugImplementation(libs.compose.ui.tooling)
}
