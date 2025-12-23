dependencyResolutionManagement {
    pluginManagement {
        repositories {
            maven("https://maven.wagyourtail.xyz/releases")
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement.versionCatalogs.create("catalog") {
    // https://github.com/palantir/gradle-git-version
    plugin("git-version", "com.palantir.git-version").version("3.+")

    plugin("shadow", "com.gradleup.shadow").version("8.+")

    plugin("unmined", "xyz.wagyourtail.unimined").version("1.+")

    val minecraft = "1.20.1"
    version("minecraft", minecraft)

    val kotlin = "2.1.21"
    version("kotlin", kotlin)
    plugin("kotlin-jvm", "org.jetbrains.kotlin.jvm").version(kotlin)
    plugin("kotlin-plugin-serialization", "org.jetbrains.kotlin.plugin.serialization").version(kotlin)

    library("kotlin-reflect", "org.jetbrains.kotlin", "kotlin-reflect").version(kotlin)

    val kotlinxSerialization = "1.7.3"
    library("kotlinx-serialization-core", "org.jetbrains.kotlinx", "kotlinx-serialization-core").version(
        kotlinxSerialization
    )
    library("kotlinx-serialization-json", "org.jetbrains.kotlinx", "kotlinx-serialization-json").version(
        kotlinxSerialization
    )

    library("kotlinx-coroutines", "org.jetbrains.kotlinx", "kotlinx-coroutines-core").version("1.9.0")

    // https://linkie.shedaniel.dev/dependencies?loader=forge
    version("forge", "47.4.10")
    library("kotlin-forge", "thedarkcolour", "kotlinforforge").version("4.12.0")

    library("mixin", "org.spongepowered", "mixin").version("0.8.7")
    val mixinextras = "0.5.0-beta.4"
    library("mixinextras-common", "io.github.llamalad7", "mixinextras-common").version(mixinextras)
    library("mixinextras-lexforge", "io.github.llamalad7", "mixinextras-forge").version(mixinextras)
    library("mixinextras-fabric", "io.github.llamalad7", "mixinextras-fabric").version(mixinextras)
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

val name: String by settings

rootProject.name = name