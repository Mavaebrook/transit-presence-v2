// build-logic/android-library.gradle.kts
// Applied to every Android library module to keep config DRY.
// Each module's build.gradle.kts just calls:
//   apply(from = rootProject.file("build-logic/android-library.gradle.kts"))

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
