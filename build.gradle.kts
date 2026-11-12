plugins {
    // Applies fabric-loom-remap on 1.21.11 and fabric-loom on 26.1+
    id("dev.kikugie.loom-back-compat")
}

// DO NOT set group = ...! (Stonecutter configures it from stonecutter.properties.toml)
version = "${sc.properties["mod.version"]}+${sc.current.version}"
base.archivesName = sc.properties["mod.id"]

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

// Versions marked as compatible when publishing on Modrinth/CurseForge
val compatibleVersions: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
    ?.asList().orEmpty().map { it.toString() }

repositories {
    /**
     * Restricts dependency search of the given groups to the given maven,
     * which keeps dependency resolution fast.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
}

dependencies {
    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Applies Mojang mappings on obfuscated versions (1.21.11); no-op on 26.1+
    loomx.applyMojangMappings()

    // Use mod{dependency type} even on 26.1+ - loom-back-compat converts them if needed
    modImplementation("net.fabricmc:fabric-loader:${sc.properties["deps.fabric_loader"]}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties["deps.fabric_api"]}")
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        // All versions share one run directory
        runDirectory = rootProject.file("run")
        // Exports transformed classes for mixin debugging
        jvmArguments.add("-Dmixin.debug.export=true")
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, value: String) {
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", sc.properties["mod.id"])
            register("name", sc.properties["mod.name"])
            register("version", sc.properties["mod.version"])
            register("minecraft", sc.properties["mod.mc_compat"])
            register("java", requiredJava.majorVersion)
            register("loader", Regex("\\d+\\.\\d+").find(sc.properties["deps.fabric_loader"])!!.value)
        }

        filesMatching("fabric.mod.json") { expand(props) }

        val mixinJava = requiredJava.majorVersion.toString()
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }
    }

    // Includes the license in the built mod jar
    withType<Jar> {
        val name = sc.properties["mod.id"]
        inputs.property("mod_id", name)
        from("../../LICENSE") { rename { "$it-$name" } }
    }

    // Builds the current version and copies the jars to build/libs/<mod version>/
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to build/libs/{mod version}/"

        inputs.property("version", sc.properties["mod.version"])
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${sc.properties["mod.version"]}"))
    }
}
