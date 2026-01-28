package pt.iscte.ambco.kmemoize.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.toLogger
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.*
import pt.iscte.ambco.kmemoize.compiler.visitor.FunctionMemoizationTransformer

class KMemoizeIrGenerationExtension(
    val messageCollector: MessageCollector
): IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        val logger = messageCollector.toLogger()
        moduleFragment.transform(FunctionMemoizationTransformer(pluginContext, logger), null)
        logger.strongWarning(moduleFragment.dumpKotlinLike())
    }
}