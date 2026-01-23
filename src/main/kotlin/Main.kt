import pt.iscte.ambco.kmemoize.api.Memoize
import kotlin.time.measureTimedValue

class Factorial {

    @Memoize
    fun factorial(n: Int): Long =
        if (n == 0) 1L
        else n * factorial(n - 1)
}

// Without: Calculated 1! + 2! + ... + 500000! = 1005876315485501977 in 1.845570500s
// With:
fun main() {
    val f = Factorial()
    val (value, time) = measureTimedValue {
        (1 .. 10000).sumOf { f.factorial(it) }
    }
    println("Calculated 1! + 2! + ... + 500000! = $value in $time")
}