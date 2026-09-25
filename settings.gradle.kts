pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Resolves/auto-provisions the JVM used to run the Gradle Daemon
    // (gradle/gradle-daemon-jvm.properties), so contributors don't need a matching JDK preinstalled.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SpriteKit"

include(":spritekit")
include(":spritekit-compose")
