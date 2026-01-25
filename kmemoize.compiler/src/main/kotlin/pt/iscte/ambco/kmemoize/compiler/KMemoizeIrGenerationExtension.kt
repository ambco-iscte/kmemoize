package pt.iscte.ambco.kmemoize.compiler

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.common.toLogger
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.Logger
import pt.iscte.ambco.kmemoize.api.Memoize
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

    private object GeneratedForMemoization: GeneratedDeclarationKey()

    private val factory: IrFactory
        get() = context.irFactory

    private val irListOf = findKotlinCollectionsFunction("listOf") {
        it.owner.parameters.singleOrNull()?.isVararg == true
    }
    private val irMutableMapOf = findKotlinCollectionsFunction("mutableMapOf") {
        it.owner.parameters.singleOrNull()?.isVararg == true
    }
    private val irMapContainsKey = context.irBuiltIns.mutableMapClass.getSimpleFunction("containsKey")!!
    private val irMapPut = context.irBuiltIns.mutableMapClass.getSimpleFunction("put")!!
    private val irMapGet = context.irBuiltIns.mutableMapClass.getSimpleFunction("get")!!

    private fun DeclarationIrBuilder.irGetMutableMapOf(
        keyType: IrType,
        valueType: IrType,
        mapType: IrType = context.irBuiltIns.mutableMapClass.typeWithArguments(keyType, valueType)
    ): IrExpression =
        irCall(irMutableMapOf, mapType, listOf(keyType, valueType))

    private fun DeclarationIrBuilder.irGetListOf(
        elementType: IrType,
        elements: List<IrExpression>
    ): IrExpression =
        irCallWithArgs(irListOf, irVararg(elementType, elements)).apply {
            typeArguments[0] = elementType
        }

    private fun listOfType(elementType: IrType): IrType =
        context.irBuiltIns.listClass.typeWithArguments(elementType)

    private fun IrFunction.dependsOnExternalVariables(): Boolean =
        false // TODO forbid it, or in the map save another map <ExternalVariable, Value> for external state?

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        if (declaration.hasAnnotation<Memoize>()) {
            if (declaration.body == null)
                logger.warning("Cannot memoize body-less function: $declaration")
            else if (!declaration.hasValueParameters)
                logger.warning("Cannot memoize function without value parameters: $declaration")
            else if (declaration.dependsOnExternalVariables())
                logger.warning("Cannot memoize function that depends on external variables: $declaration")
            else {
                val transformer = when {
                    declaration.isTopLevel -> ::memoizeTopLevelFunction
                    declaration.isStatic -> ::memoizeStaticFunction
                    declaration.isAnonymousFunction -> ::memoizeAnonymousFunction
                    declaration.dispatchReceiverParameter != null -> ::memoizeMemberFunction
                    else -> null
                }

                if (transformer == null) {
                    logger.warning("Unsupported declaration for @Memoize, skipping: $declaration")
                    return super.visitSimpleFunction(declaration)
                }

                // ArgumentsType = type of function value arguments, if all the same type, Any? otherwise
                // TODO find closest common supertype?
                val argumentsType = declaration.getValueParameters().map {
                    it.type
                }.toSet().singleOrNull()?.type ?: context.irBuiltIns.anyNType

                // List<ArgumentsType> OR SingleArgumentType (small optimization if function takes a single argument)
                val memoryKeyType =
                    declaration.getValueParameters().singleOrNull()?.type ?:
                    context.irBuiltIns.listClass.typeWithArguments(argumentsType)

                // MutableMap<List<ArgumentsType>, FunctionReturnType>
                val mapOfFunctionArgumentsToReturnValueType = context.irBuiltIns.mutableMapClass.typeWithArguments(
                    memoryKeyType,
                    declaration.returnType
                )

                transformer.isAccessible = true
                transformer.call(declaration, argumentsType, memoryKeyType, mapOfFunctionArgumentsToReturnValueType)
            }
        }
        return super.visitSimpleFunction(declaration)
    }

    private fun memoize(
        function: IrSimpleFunction,
        memory: IrField,
        memoryKeyType: IrType,
        argumentsType: IrType
    ) = with(DeclarationIrBuilder(context, function.symbol)) {
        // listOf(*arguments) OR argument, if function takes only one argument
        val memoryArgumentsKey =
            if (memoryKeyType == listOfType(argumentsType))
                irGetListOf(argumentsType, function.getValueParameters().map(::irGet))
            else
                irGet(function.getValueParameters().first())

        // isMemoized = memory.containsKey(listOf(getFunctionArguments))
        val isMemoized = irCallWithArgs(
            irGetField(null, memory),
            irMapContainsKey,
            memoryArgumentsKey
        )

        // isNotMemoized = !isMemoized
        val isNotMemoized = irNot(isMemoized)

        // if (isNotMemoized) { memory.set(listOf(getFunctionArguments), resultOfFunctionCall) }
        val ifNotContainedThenMemoize = irIfThen(
            type = context.irBuiltIns.unitType,
            condition = isNotMemoized,
            thenPart = irBlock {  }.apply {
                statements.addAll(function.body?.statements ?: emptyList())
                transform(object : IrElementTransformerVoidWithContext() {
                    // (return exp) --> (memory[listOf(getFunctionArguments)] = exp)
                    override fun visitReturn(expression: IrReturn): IrExpression {
                        if (expression.returnTargetSymbol != function.symbol)
                            return super.visitReturn(expression)
                        return irCallWithArgs(
                            irGetField(null, memory),
                            irMapPut,
                            memoryArgumentsKey,
                            expression.value
                        )
                    }
                }, null)
            }
        )

        // memoizedValue = memory.get(listOfArguments)
        val memoizedValue = irCallWithArgs(
            irGetField(null, memory),
            irMapGet,
            memoryArgumentsKey
        ).apply { type = function.returnType }

        // return memoizedValue
        val returnMemoized = irReturn(irCheckNotNull(memoizedValue)).apply {
            type = function.returnType
        }

        function.body = factory.simpleBlockBody(ifNotContainedThenMemoize, returnMemoized)
    }

    private fun getMemoizationFieldInitializer(
        function: IrSimpleFunction,
        memoryKeyType: IrType,
    ): IrExpressionBody =
        with(DeclarationIrBuilder(context, function.symbol)) {
            irExprBody(irGetMutableMapOf(memoryKeyType, function.returnType))
        }

    private fun memoizeMemberFunction(
        function: IrSimpleFunction,
        argumentsType: IrType,
        memoryKeyType: IrType,
        mapOfListOfArgumentsToReturnValueType: IrType
    ) {
        val klass = function.parent as? IrClass
        if (klass == null) {
            logger.warning("Declaring class not found for top-level function: $function")
            return
        }

        val memory = klass.addField {
            name = Name.identifier("${function.name.identifier}${function.hashCode()}memory")
            visibility = DescriptorVisibilities.PRIVATE
            type = mapOfListOfArgumentsToReturnValueType
            isFinal = true
            isStatic = false
            origin = IrDeclarationOrigin.GeneratedByPlugin(GeneratedForMemoization)
        }.apply {
            initializer = getMemoizationFieldInitializer(function, memoryKeyType)
        }

        memoize(function, memory, memoryKeyType, argumentsType)
    }

    private fun memoizeTopLevelFunction(
        function: IrSimpleFunction,
        argumentsType: IrType,
        memoryKeyType: IrType,
        mapOfListOfArgumentsToReturnValueType: IrType
    ) {
        val packageFragment = function.parent as? IrPackageFragment
        if (packageFragment == null) {
            logger.warning("Package fragment not found for top-level function: $function")
            return
        }

        val memory = factory.buildField {
            name = Name.identifier("${function.name.identifier}${function.hashCode()}memory")
            visibility = DescriptorVisibilities.PRIVATE
            type = mapOfListOfArgumentsToReturnValueType
            isFinal = true
            isStatic = true
            origin = IrDeclarationOrigin.GeneratedByPlugin(GeneratedForMemoization)
        }.apply {
            initializer = getMemoizationFieldInitializer(function, memoryKeyType)
        }
        packageFragment.addChild(memory)

        memoize(function, memory, memoryKeyType, argumentsType)
    }

    private fun memoizeStaticFunction(
        function: IrSimpleFunction,
        argumentsType: IrType,
        listOfArgumentsType: IrType,
        mapOfListOfArgumentsToReturnValueType: IrType
    ) {
        throw UnsupportedOperationException("Static functions not yet supported!")
    }

    private fun memoizeAnonymousFunction(
        function: IrSimpleFunction,
        argumentsType: IrType,
        listOfArgumentsType: IrType,
        mapOfListOfArgumentsToReturnValueType: IrType
    ) {
        throw UnsupportedOperationException("Anonymous functions not yet supported!")
    }
}

