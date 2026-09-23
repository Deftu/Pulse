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
    implementation(libs.nanohttpd)

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
