pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    // Multi-version build orchestration
    id("dev.kikugie.stonecutter") version "0.10"

    // Applies the correct Loom variant for each Minecraft version (fabric-loom-remap on <=1.21.11, fabric-loom on 26.1+)
    id("dev.kikugie.loom-back-compat") version "0.4.3"

    // Automatically downloads the required JDK (21 for 1.21.11, 25 for 26.x) if it is missing
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Every entry becomes a generated subproject in versions/<name>.
        // Add or remove versions here (and in stonecutter.properties.toml).
        versions("1.21.11", "26.1.2", "26.2", "26.3")

        // The version edited by default in the IDE
        vcsVersion = "26.3"
    }
}

rootProject.name = "DonutTweks"
