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

// Upload-key signing comes only from the ignored keystore.properties or AMAR_UPLOAD_* variables.
// Without them, release outputs are built unsigned so CI can still verify the release configuration.
val uploadProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun uploadValue(key: String): String? =
    providers.environmentVariable("AMAR_UPLOAD_$key").orNull ?: uploadProperties.getProperty(key)
val uploadStoreFile = uploadValue("STORE_FILE")?.let(rootProject::file)?.also {
    require(it.isFile) { "Upload keystore STORE_FILE does not exist; fix keystore.properties or AMAR_UPLOAD_STORE_FILE" }
}

android {
    // Kotlin package/namespace stays stable; Play identity is the application ID below.
    namespace = "com.fireamun.iptv.prototype"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fireamun.amariptv"
        minSdk = 28
        targetSdk = 36
        versionCode = 14
        versionName = "0.12.0"
        testInstrumentationRunner = "com.fireamun.iptv.prototype.ManualFixtureRunner"
        buildConfigField("String", "POSTHOG_API_KEY", safePosthogProjectToken?.let { "\"$it\"" } ?: "null")
        buildConfigField("String", "POSTHOG_HOST", safePosthogHost?.let { "\"$it\"" } ?: "null")
    }
    signingConfigs {
        if (uploadStoreFile != null) create("upload") {
            storeFile = uploadStoreFile
            storePassword = uploadValue("STORE_PASSWORD")
            keyAlias = uploadValue("KEY_ALIAS")
            keyPassword = uploadValue("KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("upload")
        }
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
