# KMemoize 🧠

[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/static/v1?label=license&message=Cooperative+Non-Violent+Public+License+(CNPLv8%2B)&color=BC8C3D&logo=googledocs&logoColor=5E2751)](https://ethicalsource.dev/)

**KMemoize** is a Kotlin compiler plugin which enables quick-and-easy function memoization
without the need for boilerplate code.

- Functions are tagged with the `@Memoize` annotation.
- Supports top-level, member, ~~static, anonymous, and lambda~~ functions :)

## ℹ️ Example
### Help! My Function Is Recursive and I’m Calling It a Lot!
```kotlin
fun factorial(n: Int): Long =
    if (n == 0) 1L
    else n * factorial(n - 1)

@Memoize
fun factorialMemoized(n: Int): Long =
    if (n == 0) 1L
    else n * factorialMemoized(n - 1)

fun main() {
    val time = measureTime { (1 .. 40000).sumOf { factorial(it) } }
    val timeMemoized = measureTime { (1 .. 40000).sumOf { factorialMemoized(it) } }
    println("Non-memoized time: $time")
    println("Memoized time: $timeMemoized")
}
```
**Console Output:**
```
Non-memoized time: 1.483682300s
Memoized time: 10.784501ms
```
This example may be contrived (who's out there regularly summing the first 40 thousand factorials?), but
it still demonstrates a 99% decrease in running time (on my machine, at least) just from adding an annotation. :)

### How Does It Work?

Internally, what's happening is that `factorialMemoized` is getting compiled to:
```kotlin
private val factorialMemoized234738315memory: MutableMap<Int, Long> = 
    mutableMapOf<Int, Long>()

@Memoize
fun factorialMemoized(n: Int): Long {
    if (n !in factorialMemoized234738315memory) {
        factorialMemoized234738315memory[n] = 
            if (n == 0) 1L 
            else n * factorialMemoized(n - 1)
    }
    return factorialMemoized234738315memory[n]!!
}
```
The weird `234738315` in `factorialMemoized234738315memory` is just the hash code of the function's
IR representation during compilation. This is just to avoid using existing identifiers.

## ⚙️ Setup
### 🐘 Gradle

Coming soon!

### 🪶 Maven

Coming soon!

## 📄 License

**KMemoize** is licensed under [The Cooperative Non-Violent Public License](), an 
[ethical source license](https://ethicalsource.dev/) which essentially says that you may use the code or parts of it
as you see fit, **but**:
- You must use the same license for any distribution or adaptation of the project.
- You must not derive any sort of commercial advantage or monetary gain unless you are a worker-owned business or worker-owned collective. (Basically, my software's not helping you make money unless it's to support workers' rights.)
- **Using it for personal projects is completely fine.** Have fun! :)

Being aware of this summary does not replace reading the [full license document](LICENSE.md).