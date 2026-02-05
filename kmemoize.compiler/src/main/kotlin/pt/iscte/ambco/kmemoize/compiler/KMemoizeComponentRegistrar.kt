@file:OptIn(ExperimentalCompilerApi::class)

package pt.iscte.ambco.kmemoize.compiler

import com.google.auto.service.AutoService
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY
import org.jetbrains.kotlin.config.CompilerConfiguration

@AutoService(CompilerPluginRegistrar::class)
class KMemoizeComponentRegistrar: CompilerPluginRegistrar() {

    companion object {
        const val PLUGIN_ID = "io.github.ambco-iscte.kmemoize"
    }

    override val supportsK2: Boolean
        get() = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val collector = configuration.get(MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        val extension = KMemoizeIrGenerationExtension(collector)
        IrGenerationExtension.registerExtension(extension)
    }
}