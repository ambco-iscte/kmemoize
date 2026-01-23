plugins {
    kotlin("jvm")
    `java-gradle-plugin`
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
    plugins {
        create("KMemoize") {
            id = "pt.iscte.ambco.kmemoize"
            displayName = "Kotlin Memoization Compiler Plugin"
            description = displayName
            implementationClass = "pt.iscte.ambco.kmemoize.gradle.KMemoizeGradleSubplugin"
        }
    }
}