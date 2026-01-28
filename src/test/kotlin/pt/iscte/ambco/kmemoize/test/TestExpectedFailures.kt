package pt.iscte.ambco.kmemoize.test

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import pt.iscte.ambco.kmemoize.BaseTest
import kotlin.test.assertContains
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class TestExpectedFailures: BaseTest() {

    @Test
    fun testWithoutBody() {
        val result = compile("""
            interface Foo {
                @pt.iscte.ambco.kmemoize.api.Memoize
                fun bar(n: Int): Int
            }
        """.trimIndent())

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Cannot @Memoize body-less function: Foo.bar")
    }

    @Test
    fun testWithoutValueParameters() {
        val result = compile("""
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun foo(): Int = 42
        """)

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Cannot @Memoize function without value parameters: foo")
    }

    @Test
    fun testWithUnsupportedReturnType() {
        val result = compile("""
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun foo(name: String) = println("Hello, " + name + "!")
        """)

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Cannot @Memoize function with return type kotlin.Unit: foo")
    }

    @Test
    fun testNotPure() {
        val result = compile("""
            var x = 42
            
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun foo(n: Int): Int {
                x++
                return n + x
            }

            @pt.iscte.ambco.kmemoize.api.Memoize
            fun bar(n: Int): Int {
                return (n * java.lang.Math.random()).toInt()
            }
        """)

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Cannot @Memoize impure function: foo")
        assertContains(result.messages, "Cannot @Memoize impure function: bar")
    }
}