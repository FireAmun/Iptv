plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.gson)
    // Self-signed TV identity and TLS contexts without BouncyCastle.
    implementation(libs.okhttp.tls)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
