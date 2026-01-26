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
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isNullableNothing
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
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

private object GeneratedForMemoizationPluginKey: GeneratedDeclarationKey()
private val GeneratedForMemoization = IrDeclarationOrigin.GeneratedByPlugin(GeneratedForMemoizationPluginKey)

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class KMemoizeIrGenerationTransformer(
    context: IrPluginContext,
    logger: Logger
): IrElementTransformerWrapper(context, logger) {

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

    private fun listOfType(elementType: IrType): IrType =
        context.irBuiltIns.listClass.typeWithArguments(elementType)

    private fun DeclarationIrBuilder.irGetListOf(
        elementType: IrType,
        elements: List<IrExpression>
    ): IrExpression =
        irCall(irListOf, listOfType(elementType), listOf(elementType)).apply {
            arguments[0] = irVararg(elementType, elements)
        }

    private fun getMemoizationField(
        function: IrSimpleFunction,
        memoryKeyType: IrType,
        mapOfListOfArgumentsToReturnValueType: IrType,
        isStatic: Boolean
    ): IrField =
        factory.buildField {
            name = Name.identifier("${function.name.identifierOrNullIfSpecial ?: "anonymous"}${function.hashCode()}Memory")
            visibility = DescriptorVisibilities.PRIVATE
            type = mapOfListOfArgumentsToReturnValueType
            isFinal = true
            this.isStatic = isStatic
            origin = GeneratedForMemoization
        }.apply {
            initializer = with(DeclarationIrBuilder(context, function.symbol)) {
                irExprBody(irGetMutableMapOf(memoryKeyType, function.returnType))
            }
        }

    private fun returnsUnsupportedType(declaration: IrSimpleFunction) =
        with(declaration.returnType) {
            isUnit() || isNothing() || isNullableNothing()
        }

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        if (declaration.hasAnnotation<Memoize>()) {
            if (declaration.body == null)
                logger.warning("Cannot @Memoize body-less function: ${declaration.kotlinFqName}")
            else if (!declaration.hasValueParameters)
                logger.warning("Cannot @Memoize function without value parameters: ${declaration.kotlinFqName}")
            else if (returnsUnsupportedType(declaration))
                logger.warning("Cannot @Memoize function with return type ${declaration.returnType.classFqName}: ${declaration.kotlinFqName}")
            else if (!declaration.isPure())
                logger.warning("Cannot @Memoize non-pure function: ${declaration.kotlinFqName}")
            else {
                val transformer = when {
                    declaration.isTopLevel -> ::memoizeTopLevelFunction
                    declaration.parent is IrClass -> ::memoizeClassFunction
                    declaration.isLocal -> ::memoizeAnonymousOrLocalFunction
                    declaration.name == SpecialNames.ANONYMOUS -> ::memoizeAnonymousOrLocalFunction
                    else -> null
                }

                if (transformer == null) {
                    logger.warning("Unsupported declaration for @Memoize, skipping: ${declaration.kotlinFqName}")
                    return super.visitSimpleFunction(declaration)
                }

                // ArgumentsType = type of function value arguments, if all the same type, closest supertype otherwise
                val parameterTypes = declaration.getValueParameters().map { it.type }
                val argumentsType =
                    parameterTypes.toSet().singleOrNull() ?: context.findClosestCommonSupertype(parameterTypes)

                // List<ArgumentsType> OR SingleArgumentType (small optimization if function takes a single argument)
                val memoryKeyType = parameterTypes.singleOrNull() ?: listOfType(argumentsType)

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

        // isMemoized = memory.containsKey(functionArguments)
        val isMemoized = irCallWithArgs(
            irGetField(null, memory),
            irMapContainsKey,
            memoryArgumentsKey
        )

        // isNotMemoized = !isMemoized
        val isNotMemoized = irNot(isMemoized)

        // if (isNotMemoized) { memory.set(functionArguments, resultOfFunctionCall) }
        val ifNotContainedThenMemoize = irIfThen(
            type = context.irBuiltIns.unitType,
            condition = isNotMemoized,
            thenPart = irBlock {  }.apply {
                statements.addAll(function.body?.statements ?: emptyList())
                transform(object : IrElementTransformerVoidWithContext() {
                    // (return exp) --> (memory[functionArguments] = exp)
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

        // memoizedValue = memory.get(functionArguments)
        val memoizedValue = irCallWithArgs(
            irGetField(null, memory),
            irMapGet,
            memoryArgumentsKey
        ).apply { type = function.returnType }

        // return memoizedValue!!
        val returnMemoized = irReturn(irCheckNotNull(memoizedValue)).apply {
            type = function.returnType
        }

        function.body = factory.simpleBlockBody(listOf(ifNotContainedThenMemoize, returnMemoized))
    }

    private fun memoizeClassFunction(
        function: IrSimpleFunction,
        argumentsType: IrType,
        memoryKeyType: IrType,
        mapOfListOfArgumentsToReturnValueType: IrType
    ) {
        val klass = function.parent as? IrClass
        if (klass == null) {
            logger.warning("Declaring class not found for function: ${function.kotlinFqName}")
            return
        }

        val memory = klass.addField {
            val backing = getMemoizationField(function, memoryKeyType, mapOfListOfArgumentsToReturnValueType, function.isStatic)
            name = backing.name
            updateFrom(backing)
        }

        memoize(function, memory, memoryKeyType, argumentsType)
    }

    private fun memoizeTopLevelFunction(
        function: IrSimpleFunction,
        argumentsType: IrType,
        memoryKeyType: IrType,
        mapOfListOfArgumentsToReturnValueType: IrType
    ) {
        val packageFragment = function.parent as? IrPackageFragment ?: (function.parent as? IrClass)?.parent as? IrPackageFragment
        if (packageFragment == null) {
            logger.warning("Package fragment not found for top-level function: ${function.kotlinFqName}")
            return
        }

        val memory = getMemoizationField(function, memoryKeyType, mapOfListOfArgumentsToReturnValueType, true)
        packageFragment.addChild(memory)

        memoize(function, memory, memoryKeyType, argumentsType)
    }

    private fun memoizeAnonymousOrLocalFunction(
        function: IrSimpleFunction,
        argumentsType: IrType,
        memoryKeyType: IrType,
        mapOfListOfArgumentsToReturnValueType: IrType
    ) {
        val container = function.findClosestAncestorContainer()
        if (container == null) {
            logger.warning("Could not find declaration container ancestor for function: ${function.kotlinFqName}")
            return
        }

        val isStatic =
            function.isStatic
            || container is IrPackageFragment
            || (container is IrClass && container.isObject)
            || function.declarationAncestors().any {
                (it as? IrFunction)?.isStatic == true
            }

        val memory = getMemoizationField(function, memoryKeyType, mapOfListOfArgumentsToReturnValueType, isStatic)
        container.addChild(memory)

        memoize(function, memory, memoryKeyType, argumentsType)
    }
}

