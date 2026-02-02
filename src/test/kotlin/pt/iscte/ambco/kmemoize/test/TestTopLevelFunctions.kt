package pt.iscte.ambco.kmemoize.test

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import pt.iscte.ambco.kmemoize.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCompilerApi::class)
class TestTopLevelFunctions: BaseTest() {

    @Test
    fun testOneParameter() {
        val result = compile("""
            fun factorial(n: Int): Long =
                if (n == 0) 1L
                else n * factorial(n - 1)
            
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun factorialMemoized(n: Int): Long =
                if (n == 0) 1L
                else n * factorialMemoized(n - 1)
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
            fun product(a: Int, b: Int): Int = a * b
        
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun productMemoized(a: Int, b: Int): Int = a * b
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
            fun product(a: Int, b: Double): Double = a * b
        
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun productMemoized(a: Int, b: Double): Double = a * b
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
            fun contains(list: List<Int>, element: Int): Boolean = element in list
        
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun containsMemoized(list: List<Int>, element: Int): Boolean = element in list
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

    @Test
    fun testGeneric() {
        val result = compile("""
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun <T> self(obj: T): T = obj
        """.trimIndent())

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("KotlinKt"))

        val self = klass.getDeclaredMethod("self", Any::class.java)

        (0 .. 100).forEach {
            assertEqualsTwice(it, self.invoke(null, it))
            assertEqualsTwice(it.toString(), self.invoke(null, it.toString()))
        }
    }
}