plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":kmemoize.api"))
    implementation(kotlin("compiler-embeddable"))
    implementation("com.google.auto.service:auto-service:1.1.1")
    compileOnly("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")
    implementation(kotlin("reflect"))
}