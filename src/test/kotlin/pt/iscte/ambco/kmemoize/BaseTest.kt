package pt.iscte.ambco.kmemoize

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import pt.iscte.ambco.kmemoize.compiler.KMemoizeComponentRegistrar
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
open class BaseTest {

    companion object {
        private val ANNOTATION_SOURCE: SourceFile
            get() {
                val source = File("kmemoize.api/src/main/kotlin/pt/iscte/ambco/kmemoize/api/Memoize.kt")
                return SourceFile.new(source.name, source.readText())
            }
    }

    protected fun compile(@Language("kotlin") source: String): JvmCompilationResult =
        KotlinCompilation().apply {
            sources = listOf(ANNOTATION_SOURCE, SourceFile.kotlin("Kotlin.kt", source.trimIndent()))
            compilerPluginRegistrars = listOf(KMemoizeComponentRegistrar())
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()
}