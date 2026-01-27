package pt.iscte.ambco.kmemoize.test

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import pt.iscte.ambco.kmemoize.ANNOTATION_SOURCE
import pt.iscte.ambco.kmemoize.compiler.KMemoizeComponentRegistrar
import kotlin.test.assertContains
import kotlin.test.assertEquals

@OptIn(ExperimentalCompilerApi::class)
class TestExpectedFailures {

    @Test
    fun testWithoutBody() {
        val source = SourceFile.kotlin("Kotlin.kt", """
            interface Foo {
                @pt.iscte.ambco.kmemoize.api.Memoize
                fun bar(n: Int): Int
            }
        """.trimIndent())

        val result = KotlinCompilation().apply {
            sources = listOf(ANNOTATION_SOURCE, source)
            compilerPluginRegistrars = listOf(KMemoizeComponentRegistrar())
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Cannot @Memoize body-less function: Foo.bar")
    }

    @Test
    fun testWithoutValueParameters() {
        val source = SourceFile.kotlin("Kotlin.kt", """
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun foo(): Int = 42
        """.trimIndent())

        val result = KotlinCompilation().apply {
            sources = listOf(ANNOTATION_SOURCE, source)
            compilerPluginRegistrars = listOf(KMemoizeComponentRegistrar())
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Cannot @Memoize function without value parameters: foo")
    }

    @Test
    fun testWithUnsupportedReturnType() {
        val source = SourceFile.kotlin("Kotlin.kt", """
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun foo(name: String) = println("Hello, " + name + "!")
        """.trimIndent())

        val result = KotlinCompilation().apply {
            sources = listOf(ANNOTATION_SOURCE, source)
            compilerPluginRegistrars = listOf(KMemoizeComponentRegistrar())
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Cannot @Memoize function with return type kotlin.Unit: foo")
    }

    @Test
    fun testNotPure() {
        val source = SourceFile.kotlin("Kotlin.kt", """
            var x = 42
            
            @pt.iscte.ambco.kmemoize.api.Memoize
            fun foo(n: Int): Int {
                x++
                return n + x
            }
        """.trimIndent())

        val result = KotlinCompilation().apply {
            sources = listOf(ANNOTATION_SOURCE, source)
            compilerPluginRegistrars = listOf(KMemoizeComponentRegistrar())
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "Cannot @Memoize non-pure function: foo")
    }
}