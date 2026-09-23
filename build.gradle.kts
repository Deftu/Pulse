plugins {
    kotlin("jvm") version("2.3.20")
    kotlin("plugin.serialization") version("2.3.20")

    val dgt = "2.75.0"
    id("dev.deftu.gradle.tools") version(dgt)
    id("dev.deftu.gradle.tools.publishing.maven") version(dgt)
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

java {
    withSourcesJar()
}

kotlin {
    explicitApi()
    jvmToolchain(21)
}

tasks {
    test {
        failOnNoDiscoveredTests = false
        useJUnitPlatform()
    }
}
