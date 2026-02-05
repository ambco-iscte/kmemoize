plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.0.0"
}

dependencies {
    compileOnly(kotlin("gradle-plugin-api"))
    compileOnly(kotlin("gradle-plugin"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("gradle-plugin-api"))
    implementation("com.google.auto.service:auto-service:1.1.1")
    compileOnly("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")
}

gradlePlugin {
    website = "https://github.com/ambco-iscte/kmemoize"
    vcsUrl = "https://github.com/ambco-iscte/kmemoize.git"
    plugins {
        create("KMemoize") {
            id = "io.github.ambco-iscte.kmemoize"
            displayName = "KMemoize"
            description = "An annotation-based Kotlin compiler plugin for function memoization."
            tags = listOf("memoization", "kotlin-plugin", "kotlin", "kotlin-compiler-plugin", "memoization-helper")
            implementationClass = "pt.iscte.ambco.kmemoize.gradle.KMemoizeGradleSubplugin"
            version = project.version.toString()
        }
    }
}