import groovy.lang.Closure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    alias(catalog.plugins.kotlin.jvm)
    alias(catalog.plugins.kotlin.plugin.serialization)

    alias(catalog.plugins.git.version)

    alias(catalog.plugins.unmined)
}

val archive_name: String by rootProject.properties
val id: String by rootProject.properties
val name: String by rootProject.properties
val author: String by rootProject.properties
val description: String by rootProject.properties
val source: String by rootProject.properties

group = "settingdust.item_converter"

val gitVersion: Closure<String> by extra
version = gitVersion()

base {
    archivesName = archive_name
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }

    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    withSourcesJar()
}

repositories {
    unimined.curseMaven()
    unimined.modrinthMaven()

    maven("https://thedarkcolour.github.io/KotlinForForge/") {
        content { includeGroup("thedarkcolour") }
    }

    maven("https://maven.nucleoid.xyz/") {
        content { includeGroup("xyz.nucleoid") }
    }
}

unimined.minecraft {
    version(catalog.versions.minecraft.get())

    mappings {
        searge()
        mojmap()
        parchment(mcVersion = "1.19.4", version = "2023.06.26")

        devFallbackNamespace("searge")
    }

    minecraftForge {
        mixinConfig("$id.mixins.json")
        loader(catalog.versions.forge.get())
    }
}

val modImplementation by configurations
val include by configurations
val minecraftLibraries by configurations

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    compileOnly(catalog.mixin)
    implementation(catalog.mixinextras.common)

    implementation(catalog.kotlin.forge)

    catalog.jgrapht.let {
        minecraftLibraries(it)
        implementation(it)
        include(it)
        minecraftLibraries("org.apfloat:apfloat:1.15.0")
        minecraftLibraries("org.jheaps:jheaps:0.14")
        include("org.apfloat:apfloat:1.15.0")
        include("org.jheaps:jheaps:0.14")
    }
}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<ProcessResources> {
        val properties = mapOf(
            "id" to id,
            "version" to rootProject.version,
            "group" to rootProject.group,
            "name" to rootProject.name,
            "description" to rootProject.property("description").toString(),
            "source" to rootProject.property("source").toString()
        )
        from(rootProject.sourceSets.main.get().resources)
        inputs.properties(properties)

        filesMatching(
            listOf(
                "META-INF/mods.toml",
                "*.mixins.json",
                "META-INF/MANIFEST.MF"
            )
        ) {
            expand(properties)
        }
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}