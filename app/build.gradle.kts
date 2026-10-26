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
        versionCode = 9
        versionName = "0.7.2"
        testInstrumentationRunner = "com.fireamun.iptv.prototype.ManualFixtureRunner"
    }
    buildFeatures { compose = true }
    sourceSets.getByName("androidTest").assets.directories.add("../core/storage/schemas")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
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
