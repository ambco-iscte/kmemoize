package pt.iscte.ambco.kmemoize.compiler.visitor

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.buildField
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irExprBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.isNothing
import org.jetbrains.kotlin.ir.types.isNullableNothing
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.addChild
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.isLocal
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.isStatic
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.isVararg
import org.jetbrains.kotlin.ir.util.kotlinFqName
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.util.Logger
import org.jetbrains.kotlin.util.WithLogger
import pt.iscte.ambco.kmemoize.api.AlwaysMemoize
import pt.iscte.ambco.kmemoize.api.Memoize
import pt.iscte.ambco.kmemoize.compiler.common.irCallBuilder
import pt.iscte.ambco.kmemoize.compiler.common.findClosestCommonSupertype
import pt.iscte.ambco.kmemoize.compiler.common.findClosestDeclarationContainer
import pt.iscte.ambco.kmemoize.compiler.common.findFunction
import pt.iscte.ambco.kmemoize.compiler.common.getSuperDeclarations
import pt.iscte.ambco.kmemoize.compiler.common.getValueParameters
import pt.iscte.ambco.kmemoize.compiler.common.hasAnnotation
import pt.iscte.ambco.kmemoize.compiler.common.hasValueParameters
import pt.iscte.ambco.kmemoize.compiler.common.irCheckNotNull
import pt.iscte.ambco.kmemoize.compiler.common.typeWithArguments
import kotlin.reflect.jvm.isAccessible

private object GeneratedForMemoizationPluginKey: GeneratedDeclarationKey()
private val GeneratedForMemoization = IrDeclarationOrigin.GeneratedByPlugin(GeneratedForMemoizationPluginKey)

@OptIn(UnsafeDuringIrConstructionAPI::class)
internal class FunctionMemoizationTransformer(
    private val context: IrPluginContext,
    override val logger: Logger
): IrElementTransformerVoidWithContext(), WithLogger {

    private val factory: IrFactory
        get() = context.irFactory

    private val irListOf = context.findFunction(CallableId(
        FqName("kotlin.collections"),
        Name.identifier("listOf")
    )) {
        it.owner.parameters.singleOrNull()?.isVararg == true
    }

    private val irMutableMapOf = context.findFunction(CallableId(
        FqName("kotlin.collections"),
        Name.identifier("mutableMapOf")
    )) {
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
        val isForciblyMemoized = declaration.hasAnnotation(AlwaysMemoize::class)
        if (declaration.hasAnnotation<Memoize>() || isForciblyMemoized) {
            if (declaration.body == null)
                logger.error("Cannot @Memoize body-less function: ${declaration.kotlinFqName}")
            else if (!declaration.hasValueParameters)
                logger.error("Cannot @Memoize function without value parameters: ${declaration.kotlinFqName}")
            else if (returnsUnsupportedType(declaration))
                logger.error("Cannot @Memoize function with return type ${declaration.returnType.classFqName}: ${declaration.kotlinFqName}")
            else if (!isForciblyMemoized && !context.isPure(declaration, logger))
                logger.error("Cannot @Memoize non-pure function: ${declaration.kotlinFqName}")
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
        val isMemoized =
            irCallBuilder(irMapContainsKey).
            withReceiver(irGetField(null, memory)).
            withArguments(memoryArgumentsKey).
            build()

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
                        return irCallBuilder(irMapPut).
                        withReceiver(irGetField(null, memory)).
                        withArguments(memoryArgumentsKey, expression.value).
                        build()
                    }
                }, null)
            }
        )

        // memoizedValue = memory.get(functionArguments)
        val memoizedValue = irCallBuilder(irMapGet).
        withReceiver(irGetField(null, memory)).
        withArguments(memoryArgumentsKey).
        build().apply {
            type = function.returnType
        }

        // return memoizedValue!!
        val returnMemoized = irReturn(irCheckNotNull(memoizedValue)).apply {
            type = function.returnType
        }

        function.body = irBlockBody {  }.apply {
            statements.add(ifNotContainedThenMemoize)
            statements.add(returnMemoized)
        }
    }

    private fun memoizeClassFunction(
        function: IrSimpleFunction,
        argumentsType: IrType,
        memoryKeyType: IrType,
        mapOfListOfArgumentsToReturnValueType: IrType
    ) {
        val klass = function.parent as? IrClass
        if (klass == null) {
            logger.error("Declaring class not found for function: ${function.kotlinFqName}")
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
            logger.error("Package fragment not found for top-level function: ${function.kotlinFqName}")
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
        val container = function.findClosestDeclarationContainer()
        if (container == null) {
            logger.error("Could not find declaration container ancestor for function: ${function.kotlinFqName}")
            return
        }

        val isStatic =
            function.isStatic
                    || container is IrPackageFragment
                    || (container is IrClass && container.isObject)
                    || function.getSuperDeclarations().any {
                (it as? IrFunction)?.isStatic == true
            }

        val memory = getMemoizationField(function, memoryKeyType, mapOfListOfArgumentsToReturnValueType, isStatic)
        container.addChild(memory)

        memoize(function, memory, memoryKeyType, argumentsType)
    }
}