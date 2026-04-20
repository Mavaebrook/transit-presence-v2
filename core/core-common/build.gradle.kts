plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        // Reads from the environment variable 'MAPS_API_KEY' if it exists, 
        // otherwise uses a placeholder string.
        val mapsKey: String = System.getenv("MAPS_API_KEY") ?: "AIzaSyC9HCFtqOrR9ZCwIisVm-FJ5OIeU88Ort0"
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
    }
}
