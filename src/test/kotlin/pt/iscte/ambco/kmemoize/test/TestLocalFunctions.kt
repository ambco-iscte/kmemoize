package pt.iscte.ambco.kmemoize.test

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import pt.iscte.ambco.kmemoize.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCompilerApi::class)
class TestLocalFunctions: BaseTest() {

    @Test
    fun testOneParameter() {
        val result = compile("""
            fun factorial(n: Int): Long {
                fun foo(m: Int): Long = 
                    if (m == 0) 1L
                    else m * foo(m - 1)
                return foo(n)
            }
           
            fun factorialMemoized(n: Int): Long {
                @pt.iscte.ambco.kmemoize.api.Memoize
                fun fooMemoized(m: Int): Long = 
                    if (m == 0) 1L
                    else m * fooMemoized(m - 1)

                return fooMemoized(n)
            }
        """.trimIndent())

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("KotlinKt"))

        val factorial = klass.getDeclaredMethod("factorial", Int::class.java)
        val factorialMemoized = klass.getDeclaredMethod("factorialMemoized", Int::class.java)

        (0 .. 100).forEach {
            assertEqualsTwice(
                factorial.invoke(null, it),
                factorialMemoized.invoke(null, it)
            )

            assertEqualsTwice(
                (0 .. it).sumOf { factorial.invoke(null, it) as Long },
                (0 .. it).sumOf { factorialMemoized.invoke(null, it) as Long }
            )
        }
    }

    @Test
    fun testTwoParametersSameType() {
        val result = compile("""
            fun product(a: Int, b: Int): Int {
                fun foo(p: Int, q: Int): Int = p * q
                return foo(a, b)
            }
        
            fun productMemoized(a: Int, b: Int): Int {
                @pt.iscte.ambco.kmemoize.api.Memoize
                fun fooMemoized(p: Int, q: Int): Int = p * q

                return fooMemoized(a, b)
            }
        """.trimIndent())

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("KotlinKt"))

        val product = klass.getDeclaredMethod("product", Int::class.java, Int::class.java)
        val productMemoized = klass.getDeclaredMethod("productMemoized", Int::class.java, Int::class.java)

        (0 .. 100).forEach {
            assertEqualsTwice(
                product.invoke(null, it, it),
                productMemoized.invoke(null, it, it)
            )
        }
    }

    @Test
    fun testTwoParametersCommonSupertype() {
        val result = compile("""
            fun product(a: Int, b: Double): Double {
                fun foo(p: Int, q: Double): Double = p * q
                return foo(a, b)
            }
        
            fun productMemoized(a: Int, b: Double): Double {
                @pt.iscte.ambco.kmemoize.api.Memoize
                fun fooMemoized(p: Int, q: Double): Double = p * q

                return fooMemoized(a, b)
            }
        """.trimIndent())

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("KotlinKt"))

        val product = klass.getDeclaredMethod("product", Int::class.java, Double::class.java)
        val productMemoized = klass.getDeclaredMethod("productMemoized", Int::class.java, Double::class.java)

        (0 .. 100).forEach {
            assertEqualsTwice(
                product.invoke(null, it, it),
                productMemoized.invoke(null, it, it)
            )
        }
    }

    @Test
    fun testTwoParametersSupertypeAny() {
        val result = compile("""
            fun contains(list: List<Int>, element: Int): Boolean {
                fun foo(lst: List<Int>, e: Int) = e in lst
                return foo(list, element)
            }
        
            fun containsMemoized(list: List<Int>, element: Int): Boolean {
                @pt.iscte.ambco.kmemoize.api.Memoize
                fun fooMemoized(lst: List<Int>, e: Int) = e in lst
            
                return fooMemoized(list, element)
            }
        """.trimIndent())

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("KotlinKt"))

        val contains = klass.getDeclaredMethod("contains", List::class.java, Int::class.java)
        val containsMemoized = klass.getDeclaredMethod("containsMemoized", List::class.java, Int::class.java)

        (0 .. 100).forEach {
            assertEqualsTwice(
                contains.invoke(null, (50..100).toList(), it),
                containsMemoized.invoke(null, (50..100).toList(), it)
            )
        }
    }
}