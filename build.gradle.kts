description = "Kotlin Memoization Compiler Plugin"


plugins {
    kotlin("jvm") version "2.2.0"
}

private val _version = "0.2.2"

group = "pt.iscte.ambco"
version = _version

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.kapt")

    group = "pt.iscte.ambco"
    version = _version

    repositories {
        mavenCentral()
        mavenLocal()
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(project("kmemoize.compiler"))
    kotlinCompilerPluginClasspath(project("kmemoize.compiler"))
    implementation(project("kmemoize.api"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}