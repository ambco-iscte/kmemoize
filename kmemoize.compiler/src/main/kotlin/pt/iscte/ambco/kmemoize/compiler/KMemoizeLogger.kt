package pt.iscte.ambco.kmemoize.compiler

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

data class KMemoizeLogger(
    val collector: MessageCollector
) {
    fun info(message: String, vararg args: Any?) {
        collector.report(CompilerMessageSeverity.INFO, message.format(*args))
    }

    fun warn(message: String, vararg args: Any?) {
        collector.report(CompilerMessageSeverity.WARNING, message.format(*args))
    }

    fun error(message: String, vararg args: Any?) {
        collector.report(CompilerMessageSeverity.ERROR, message.format(*args))
    }
}