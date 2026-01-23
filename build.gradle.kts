description = "Kotlin Memoization Compiler Plugin"


plugins {
    kotlin("jvm") version "2.2.0"
}

group = "pt.iscte.ambco"
version = "0.0.1"

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.kapt")

    group = "pt.iscte.ambco"
    version = "0.0.1"

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