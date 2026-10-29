import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val posthogProperties = Properties().apply {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) envFile.inputStream().use(::load)
}
val posthogProjectToken = providers.gradleProperty("POSTHOG_API_KEY").orNull
    ?: providers.environmentVariable("POSTHOG_API_KEY").orNull
    ?: posthogProperties.getProperty("POSTHOG_API_KEY")
val safePosthogProjectToken = posthogProjectToken?.trim()?.takeIf { it.matches(Regex("phc_[A-Za-z0-9_-]+")) }
val posthogHost = providers.gradleProperty("POSTHOG_HOST").orNull
    ?: providers.environmentVariable("POSTHOG_HOST").orNull
    ?: posthogProperties.getProperty("POSTHOG_HOST")
val safePosthogHost = posthogHost?.trim()?.takeIf { it in setOf("https://us.i.posthog.com", "https://eu.i.posthog.com") }

android {
    namespace = "com.fireamun.iptv.prototype"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fireamun.iptv.prototype"
        minSdk = 28
        targetSdk = 36
        versionCode = 10
        versionName = "0.8.0"
        testInstrumentationRunner = "com.fireamun.iptv.prototype.ManualFixtureRunner"
        buildConfigField("String", "POSTHOG_API_KEY", safePosthogProjectToken?.let { "\"$it\"" } ?: "null")
        buildConfigField("String", "POSTHOG_HOST", safePosthogHost?.let { "\"$it\"" } ?: "null")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets.getByName("androidTest").assets.directories.add("../core/storage/schemas")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(libs.posthog.android)
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:storage"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:playback"))
    implementation(project(":ui:mobile"))
    implementation(project(":ui:tv"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.compose)
    implementation(libs.coroutines.core)
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)
    implementation(libs.compose.material3)
    implementation(libs.tv.material)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.media3.exoplayer)
    androidTestImplementation(libs.media3.ui)
    debugImplementation(libs.compose.ui.test.manifest)
}
