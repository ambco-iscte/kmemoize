import pt.iscte.ambco.kmemoize.api.Memoize
import kotlin.time.measureTimedValue

fun factorial(n: Int): Long =
    if (n == 0) 1L
    else n * factorial(n - 1)

@Memoize
fun factorialMemoized(n: Int): Long =
    if (n == 0) 1L
    else n * factorialMemoized(n - 1)

// true
// Non-memoized time: 1.483682300s
// Memoized time: 10.784501ms
fun main() {
    val (value, time) = measureTimedValue { (1 .. 40000).sumOf { factorial(it) } }
    val (valueMemoized, timeMemoized) = measureTimedValue { (1 .. 40000).sumOf { factorialMemoized(it) } }
    println(value == valueMemoized)
    println("Non-memoized time: $time")
    println("Memoized time: $timeMemoized")
}