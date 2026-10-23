plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.fireamun.iptv.prototype"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.fireamun.iptv.prototype"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":ui:mobile"))
    implementation(project(":ui:tv"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.appcompat)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
