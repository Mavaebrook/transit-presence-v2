plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin module — no Android dependencies.
// This means unit tests run on the JVM without an emulator.
dependencies {
    testImplementation(libs.junit)
}
