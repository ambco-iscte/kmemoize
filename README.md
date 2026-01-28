# KMemoize 🧠

[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![license - GNU GPLv3](https://img.shields.io/static/v1?label=license&message=GNU+GPLv3&color=2ea44f)](https://opensource.org/license/gpl-3-0)

![](header.png)

**KMemoize** is a Kotlin compiler plugin which enables quick-and-easy function memoization
without the need for boilerplate code.

- Functions are tagged with the `@Memoize` annotation.
- Supports **pure** top-level, member, local, and anonymous functions :)
  - Impure functions can be forcibly memoized by using the `@AlwaysMemoize` annotation. **This may lead to unexpected results!**

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

Coming soon!

### 🪶 Maven

Coming soon!