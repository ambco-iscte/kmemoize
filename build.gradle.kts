description = "Kotlin Memoization Compiler Plugin"


plugins {
    kotlin("jvm") version "2.2.0"
}

private val _version = "0.4.2"

group = "io.github.ambco-iscte"
version = _version

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.kapt")

    group = "io.github.ambco-iscte"
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
    implementation(project("kmemoize.compiler"))
    implementation(project("kmemoize.api"))

    testImplementation("dev.zacsweers.kctfork:core:0.12.1")
    testApi("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testApi("org.junit.platform:junit-platform-suite:1.9.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    testImplementation("org.jetbrains:annotations:26.0.2-1")
}

tasks.test {
    useJUnitPlatform()
    reports.junitXml.required = true
}

tasks.named<Test>("test") {
    reports.junitXml.required = true
}

kotlin {
    jvmToolchain(21)
}