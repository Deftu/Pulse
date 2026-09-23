plugins {
    kotlin("jvm")

    id("dev.deftu.gradle.tools")
    id("dev.deftu.gradle.tools.publishing.maven")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":"))
    implementation(libs.ktor.server.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.server.test.host)
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
