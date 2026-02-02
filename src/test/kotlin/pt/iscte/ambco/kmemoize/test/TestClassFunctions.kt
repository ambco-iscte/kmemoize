package pt.iscte.ambco.kmemoize.test

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import pt.iscte.ambco.kmemoize.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCompilerApi::class)
class TestClassFunctions: BaseTest() {

    @Test
    fun testOneParameter() {
        val result = compile("""
            class Kotlin {
                fun factorial(n: Int): Long =
                    if (n == 0) 1L
                    else n * factorial(n - 1)
                
                @pt.iscte.ambco.kmemoize.api.Memoize
                fun factorialMemoized(n: Int): Long =
                    if (n == 0) 1L
                    else n * factorialMemoized(n - 1)
            }
        """.trimIndent())

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("Kotlin"))

        val caller = assertNotNull(klass.getConstructor().newInstance())

        val factorial = klass.getDeclaredMethod("factorial", Int::class.java)
        val factorialMemoized = klass.getDeclaredMethod("factorialMemoized", Int::class.java)

        (0 .. 100).forEach {
            assertEqualsTwice(
                factorial.invoke(caller, it),
                factorialMemoized.invoke(caller, it)
            )

            assertEqualsTwice(
                (0 .. it).sumOf { factorial.invoke(caller, it) as Long },
                (0 .. it).sumOf { factorialMemoized.invoke(caller, it) as Long }
            )
        }
    }

    @Test
    fun testTwoParametersSameType() {
        val result = compile("""
            class Kotlin {
                fun product(a: Int, b: Int): Int = a * b
        
                @pt.iscte.ambco.kmemoize.api.Memoize
                fun productMemoized(a: Int, b: Int): Int = a * b
            }
        """.trimIndent())

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("Kotlin"))

        val caller = assertNotNull(klass.getConstructor().newInstance())

        val product = klass.getDeclaredMethod("product", Int::class.java, Int::class.java)
        val productMemoized = klass.getDeclaredMethod("productMemoized", Int::class.java, Int::class.java)

        (0 .. 100).forEach {
            assertEqualsTwice(
                product.invoke(caller, it, it),
                productMemoized.invoke(caller, it, it)
            )
        }
    }

    @Test
    fun testTwoParametersCommonSupertype() {
        val result = compile("""
            class Kotlin {
                fun product(a: Int, b: Double): Double = a * b
            
                @pt.iscte.ambco.kmemoize.api.Memoize
                fun productMemoized(a: Int, b: Double): Double = a * b
            }
        """.trimIndent())

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("Kotlin"))

        val caller = assertNotNull(klass.getConstructor().newInstance())

        val product = klass.getDeclaredMethod("product", Int::class.java, Double::class.java)
        val productMemoized = klass.getDeclaredMethod("productMemoized", Int::class.java, Double::class.java)

        (0 .. 100).forEach {
            assertEqualsTwice(
                product.invoke(caller, it, it),
                productMemoized.invoke(caller, it, it)
            )
        }
    }

    @Test
    fun testTwoParametersSupertypeAny() {
        val result = compile("""
            class Kotlin {
                fun contains(list: List<Int>, element: Int): Boolean = element in list
        
                @pt.iscte.ambco.kmemoize.api.Memoize
                fun containsMemoized(list: List<Int>, element: Int): Boolean = element in list
            }
        """.trimIndent())

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("Kotlin"))

        val caller = assertNotNull(klass.getConstructor().newInstance())

        val contains = klass.getDeclaredMethod("contains", List::class.java, Int::class.java)
        val containsMemoized = klass.getDeclaredMethod("containsMemoized", List::class.java, Int::class.java)

        (0 .. 100).forEach {
            assertEqualsTwice(
                contains.invoke(caller, (50..100).toList(), it),
                containsMemoized.invoke(caller, (50..100).toList(), it)
            )
        }
    }
}