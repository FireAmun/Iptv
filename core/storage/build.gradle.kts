plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.fireamun.iptv.core.storage"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(libs.coroutines.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
}
