package pt.iscte.ambco.kmemoize.test

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import pt.iscte.ambco.kmemoize.BaseTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCompilerApi::class)
class TestAnonymousFunctions: BaseTest() {

    @Test
    fun testOneParameterHardcoded() {
        val result = compile("""
            fun findFirstOrNull(list: List<Int>, predicate: (Int) -> Boolean): Int? {
                for (element in list) {
                    if (predicate(element))
                        return element
                }
                return null
            }

            fun firstBiggerThan5(list: List<Int>): Int? =
                findFirstOrNull(list) @pt.iscte.ambco.kmemoize.api.Memoize { it > 5 }
        """)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("KotlinKt"))

        val firstBiggerThan5 = klass.getDeclaredMethod("firstBiggerThan5", List::class.java)

        (0 .. 100).forEach {
            if (it <= 5)
                assertEqualsTwice(null, firstBiggerThan5.invoke(null, (0..it).toList()))
            else
                assertEqualsTwice(6, firstBiggerThan5.invoke(null, (0..it).toList()))
        }
    }

    @Test
    fun testTwoParameters() {
        val result = compile("""
            fun findFirstOrNull(list: List<Pair<Int, Int>>, predicate: (Int, Int) -> Boolean): Pair<Int, Int>? {
                for (element in list) {
                    if (predicate(element.first, element.second))
                        return element
                }
                return null
            }

            fun firstBiggerThan(list: List<Pair<Int, Int>>, e: Int): Pair<Int, Int>? =
                findFirstOrNull(list) @pt.iscte.ambco.kmemoize.api.Memoize { x, y -> x > e && y > e }
        """)

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val klass = assertNotNull(result.classLoader.loadClass("KotlinKt"))

        val firstBiggerThan = klass.getDeclaredMethod("firstBiggerThan", List::class.java, Int::class.java)

        (0 .. 100).forEach {
            if (it <= 42)
                assertEqualsTwice(null, firstBiggerThan.invoke(null, (0..it).map { it to it }, 42))
            else
                assertEqualsTwice(43 to 43, firstBiggerThan.invoke(null, (0..it).map { it to it }, 42))
        }
    }
}