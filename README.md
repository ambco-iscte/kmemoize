# KMemoize 🧠

[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![license - GNU GPLv3](https://img.shields.io/static/v1?label=license&message=GNU+GPLv3&color=a32d2a&logo=gnu)](https://www.gnu.org/licenses/gpl-3.0.en.html#license-text)
![tests](https://img.shields.io/endpoint?url=https://gist.githubusercontent.com/ambco-iscte/0eb9cf379bdc633222ca940f62e74836/raw/3a36cc354405def7a2184fab21bb772253ff430f/kmemoize-junit-tests.json)
[![Gradle Plugin Portal Version](https://img.shields.io/gradle-plugin-portal/v/io.github.ambco-iscte.kmemoize?logo=gradle&label=Gradle%20Plugin%20Portal&labelColor=1DA2BD&color=gray)](https://plugins.gradle.org/plugin/io.github.ambco-iscte.kmemoize)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.ambco-iscte/kmemoize.compiler?logo=apachemaven&label=Maven%20Central&labelColor=2a4d70&color=gray)](https://central.sonatype.com/artifact/io.github.ambco-iscte/kmemoize.compiler)

![](header.png)

**KMemoize** is a Kotlin compiler plugin which enables quick-and-easy function memoization
without the need for boilerplate code.

- Functions are tagged with the `@Memoize` annotation.
- Supports **pure** top-level, member, local, and anonymous functions :)
  - Impure functions can be forcibly memoized by using the `@UnsafeMemoize` annotation. **This may lead to unintended behaviour!**

<br>

##  🧠 Example
### 🔄 Help! My Function Is Recursive and I’m Calling It a Lot!
```kotlin
fun factorial(n: Int): Long =
    if (n == 0) 1L
    else n * factorial(n - 1)

@Memoize
fun factorialMemoized(n: Int): Long =
    if (n == 0) 1L
    else n * factorialMemoized(n - 1)

fun main() {
    val (value, time) = measureTimedValue { (1 .. 40000).sumOf { factorial(it) } }
    val (valueMemoized, timeMemoized) = measureTimedValue { (1 .. 40000).sumOf { factorialMemoized(it) } }
    println(value == valueMemoized)
    println("Non-memoized time: $time")
    println("Memoized time: $timeMemoized")
}
```
**Console Output:**
```
true
Non-memoized time: 1.483682300s
Memoized time: 10.784501ms
```

### 🔍 How Does It Work?

Internally, what's happening is that `factorialMemoized` is getting compiled to the equivalent of this code 👇
```kotlin
private val factorialMemoized234738315Memory: MutableMap<Int, Long> = 
    mutableMapOf<Int, Long>()

@Memoize
fun factorialMemoized(n: Int): Long {
    if (n !in factorialMemoized234738315Memory) {
        factorialMemoized234738315Memory[n] = 
            if (n == 0) 1L 
            else n * factorialMemoized(n - 1)
    }
    return factorialMemoized234738315Memory[n]!!
}
```
The weird `234738315` in `factorialMemoized234738315Memory` is just the hash code of the function's
IR representation during compilation. This is just to avoid using existing identifiers.

<br>

## ⚙️ Setup

### 🐘 Gradle

![Gradle Plugin Portal Version](https://img.shields.io/gradle-plugin-portal/v/io.github.ambco-iscte.kmemoize?logo=gradle&label=Gradle%20Plugin%20Portal&labelColor=1DA2BD&color=gray)

```kotlin
plugins { 
    id("io.github.ambco-iscte.kmemoize") version "<kmemoize.version>"
}

dependencies {
    implementation("io.github.ambco-iscte:kmemoize.api:<kmemoize.version>")
}
```

### 🪶 Maven

[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.ambco-iscte/kmemoize.compiler?logo=apachemaven&label=Maven%20Central&labelColor=2a4d70&color=gray)](https://central.sonatype.com/artifact/io.github.ambco-iscte/kmemoize.compiler)

```xml
<dependencies>
    <dependency>
        <groupId>io.github.ambco-iscte</groupId>
        <artifactId>kmemoize.api</artifactId>
        <version>${kmemoize.version}</version>
    </dependency>
</dependencies>

<build>
<plugins>
    <plugin>
        <groupId>org.jetbrains.kotlin</groupId>
        <artifactId>kotlin-maven-plugin</artifactId>
        <version>2.2.0</version>

        <executions>
            <execution>
                <id>compile</id>
                <goals>
                    <goal>compile</goal>
                </goals>
                <configuration>
                    <compilerPlugins>
                        <plugin>kmemoize</plugin>
                    </compilerPlugins>
                </configuration>
            </execution>
        </executions>

        <dependencies>
            <dependency>
                <groupId>io.github.ambco-iscte</groupId>
                <artifactId>kmemoize.compiler</artifactId>
                <version>${kmemoize.version}</version>
            </dependency>
        </dependencies>
    </plugin>
</plugins>
</build>
```