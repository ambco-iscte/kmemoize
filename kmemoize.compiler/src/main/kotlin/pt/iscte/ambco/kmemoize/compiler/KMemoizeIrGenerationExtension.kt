package pt.iscte.ambco.kmemoize.compiler

import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.common.toLogger
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.Logger
import pt.iscte.ambco.kmemoize.api.Memoize
import pt.iscte.ambco.kmemoize.compiler.common.*
import kotlin.reflect.jvm.isAccessible

class KMemoizeIrGenerationExtension(
    val messageCollector: MessageCollector
): IrGenerationExtension {
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        val logger = messageCollector.toLogger()
        moduleFragment.transform(KMemoizeIrGenerationTransformer(pluginContext, logger), null)
        logger.strongWarning(moduleFragment.dumpKotlinLike())
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class KMemoizeIrGenerationTransformer(
    context: IrPluginContext,
    logger: Logger
): IrElementTransformerWrapper(context, logger) {

    private val factory: IrFactory
        get() = context.irFactory

    private val irListOf = findFunction("kotlin.collections", "listOf") {
        it.owner.parameters.singleOrNull()?.isVararg == true
    }
    private val irMutableMapOf = findFunction("kotlin.collections", "mutableMapOf") {
        it.owner.parameters.singleOrNull()?.isVararg == true
    }
    private val irMapContainsKey = context.irBuiltIns.mutableMapClass.getSimpleFunction("containsKey")!!
    private val irMapPut = context.irBuiltIns.mutableMapClass.getSimpleFunction("put")!!
    private val irMapGet = context.irBuiltIns.mutableMapClass.getSimpleFunction("get")!!

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        if (declaration.body != null && declaration.hasValueParameters && declaration.hasAnnotation<Memoize>()) {
            when {
                declaration.isTopLevel -> ::memoizeTopLevelFunction
                declaration.isStatic -> ::memoizeStaticFunction
                declaration.isAnonymousFunction -> ::memoizeAnonymousFunction
                declaration.dispatchReceiverParameter != null -> ::memoizeMemberFunction
                else -> null
            }?.let {
                it.isAccessible = true
                it.call(declaration)
            } ?: logger.error("Unsupported: $declaration")
        }
        return super.visitSimpleFunction(declaration)
    }

    private fun memoizeMemberFunction(function: IrSimpleFunction) {
        val klass = function.parent as? IrClass ?: return

        val argumentsType =
            if (function.getValueParameters().map { it.type }.toSet().size == 1)
                function.getValueParameters().first().type
            else context.irBuiltIns.anyNType

        val listOfAny = context.irBuiltIns.listClass.typeWithArguments(argumentsType)
        val mapOfListToRetType = context.irBuiltIns.mutableMapClass.typeWithArguments(listOfAny, function.returnType)

        // Inner Function
        val inner = klass.addFunction {
            name = Name.identifier("${function.name.identifier}_aux")
            returnType = function.returnType
            modality = function.modality
            visibility = DescriptorVisibilities.PRIVATE
        }.apply {
            parameters = function.parameters.toList()
            body = factory.simpleBlockBody(function.body?.statements?.toList() ?: emptyList())
        }

        with(DeclarationIrBuilder(context, function.symbol)) {
            // Adds a field `function_memory = mutableMapOf<List<*>, function.returnType>()`
            // to the function's declaring class
            val memory = klass.addField {
                name = Name.identifier("${function.name.identifier}_memory")
                visibility = DescriptorVisibilities.PRIVATE
                type = mapOfListToRetType
                isFinal = true
            }.apply {
                initializer = irExprBody(irCall(
                    callee = irMutableMapOf,
                    type = mapOfListToRetType,
                    typeArguments = listOf(listOfAny, function.returnType)
                ))
            }

            // listOfArguments = listOf(getFunctionArguments)
            val listOfArguments = irCallWithArgs(
                irListOf,
                *function.parameters.filter { !it.isDispatchReceiver }.mapToArray(::irGet)
            ).apply { typeArguments[0] = argumentsType }

            // isMemoized = memory.containsKey(listOfArguments)
            val isMemoized = irCallWithArgs(
                irGetField(null, memory),
                irMapContainsKey,
                listOfArguments
            )

            // isNotMemoized = !isMemoized
            val isNotMemoized = irNot(isMemoized)

            // if (isNotMemoized) { memory.set(listOfArguments, resultOfFunctionCall) }
            val ifNotContainedThenMemoize = irIfThen(
                type = context.irBuiltIns.unitType,
                condition = isNotMemoized,
                thenPart = irCallWithArgs(
                    irGetField(null, memory),
                    irMapPut,
                    listOfArguments,
                    irCallWithArgs(inner, *function.parameters.mapToArray(::irGet))
                )
            )

            // memoizedValue = memory.get(listOfArguments)
            val memoizedValue = irCallWithArgs(
                irGetField(null, memory),
                irMapGet,
                listOfArguments
            )

            // return memoizedValue
            val returnMemoized = irReturn(memoizedValue)

            // TODO transformer rename function

            function.body = factory.simpleBlockBody(ifNotContainedThenMemoize, returnMemoized)
        }
    }

    private fun memoizeTopLevelFunction(function: IrSimpleFunction) {
        throw UnsupportedOperationException("Top-level functions not yet supported!")
    }

    private fun memoizeStaticFunction(function: IrSimpleFunction) {
        throw UnsupportedOperationException("Static functions not yet supported!")
    }

    private fun memoizeAnonymousFunction(function: IrSimpleFunction) {
        throw UnsupportedOperationException("Anonymous functions not yet supported!")
    }
}

