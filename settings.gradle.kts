pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Iptv"
include(":app", ":ui:mobile", ":ui:tv", ":core:model", ":core:data", ":core:playback")
include(":core:designsystem")
include(":core:storage")
