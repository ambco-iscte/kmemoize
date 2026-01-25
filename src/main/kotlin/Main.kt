import pt.iscte.ambco.kmemoize.api.Memoize
import kotlin.time.measureTime

fun factorial(n: Int): Long =
    if (n == 0) 1L
    else n * factorial(n - 1)

@Memoize
fun factorialMemoized(n: Int): Long =
    if (n == 0) 1L
    else n * factorialMemoized(n - 1)

// Non-memoized time: 1.479797899s
// Memoized time: 45.616900ms
fun main() {
    val time = measureTime { (1 .. 40000).sumOf { factorial(it) } }
    val timeMemoized = measureTime { (1 .. 40000).sumOf { factorialMemoized(it) } }
    println("Non-memoized time: $time")
    println("Memoized time: $timeMemoized")
}