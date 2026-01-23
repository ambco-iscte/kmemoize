plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "kmemoize"
include("kmemoize.gradle")
include("kmemoize.compiler")
include("kmemoize.api")