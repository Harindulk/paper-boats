plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.ktlint)
}

// One style for every module: ktlint checks all Kotlin source sets and the .gradle.kts scripts.
val ktlintPluginId = libs.plugins.ktlint.get().pluginId
val ktlintVersion = libs.versions.ktlint.get()

allprojects {
    apply(plugin = ktlintPluginId)
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintVersion)
    }
}
