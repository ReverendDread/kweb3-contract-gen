import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.hlwgroup.kweb3"
version = "1.0.0"

repositories {
    mavenCentral()

    maven("https://jitpack.io") {
        // We explicitly include only the following groups from Jitpack to avoid conflicts.
        // For `io.github.microutils:kotlin-logging-jvm` we use the version from Maven Central
        // as the Jitpack version returns an empty jar for some reason.
        content {
            includeGroup("com.github.klepto")
        }
    }
}

dependencies {
    api("com.github.klepto:kweb3:d52c74ba03")
    api("org.web3j:core:4.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.5")
    implementation("com.squareup:kotlinpoet:1.14.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}