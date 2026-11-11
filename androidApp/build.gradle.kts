plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.starfungames.paperboats.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.starfungames.paperboats"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(libs.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.ktor.client.cio)
    testImplementation(libs.kotlin.test.junit)
}
