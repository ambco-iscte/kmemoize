plugins {
    kotlin("jvm")
    signing
    id("com.vanniktech.maven.publish") version "0.36.0"
}

dependencies {
    implementation(project(":kmemoize.api"))
    implementation(kotlin("compiler-embeddable"))
    implementation("com.google.auto.service:auto-service:1.1.1")
    compileOnly("com.google.auto.service:auto-service:1.1.1")
    kapt("com.google.auto.service:auto-service:1.1.1")
    implementation(kotlin("reflect"))
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(project.group.toString(), "kmemoize.compiler", project.version.toString())
    pom {
        name = "KMemoize Compiler Plugin"
        description = "An annotation-based Kotlin compiler plugin for function memoization."
        inceptionYear = "2026"
        version = project.version.toString()
        url = "https://github.com/ambco-iscte/kmemoize"
        licenses {
            license {
                name = "GNU General Public License, Version 3.0"
                url = "https://www.gnu.org/licenses/gpl-3.0.en.html#license-text"
                distribution = "https://www.gnu.org/licenses/gpl-3.0.en.html#license-text"
            }
        }
        developers {
            developer {
                id = "ambco"
                name = "Afonso B. Caniço"
                email = "ambco@iscte-iul.pt"
            }
        }
        scm {
            url = "https://github.com/ambco-iscte/kmemoize"
            connection = "scm:git:git://github.com/ambco-iscte/kmemoize.git"
            developerConnection = "scm:git:ssh://git@github.com/ambco-iscte/kmemoize.git"
        }
    }
}