@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package pt.iscte.ambco.kmemoize.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.ir.isPure
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeWithArguments
import org.jetbrains.kotlin.ir.util.isAnnotation
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.superTypes
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName
import kotlin.math.max

internal fun IrDeclaration.findClosestAncestorContainer(): IrDeclarationContainer? {
    var current: IrDeclarationParent = this.parent
    while (current !is IrDeclarationContainer)
        current = (current as? IrDeclaration)?.parent ?: return null
    return current
}

internal fun IrDeclaration.declarationAncestors(): Sequence<IrDeclaration> =
    sequence {
        var current: IrDeclarationParent? = this@declarationAncestors.parent
        while (current != null) {
            (current as? IrDeclaration)?.let { yield(it) }
            current = (current as? IrDeclaration)?.parent
        }
    }

private fun getAllSupertypes(type: IrType): Map<IrType, Int> {
    val map = mutableMapOf<IrType, Int>()
    fun traverse(current: IrType, depth: Int) {
        map[current] = depth
        current.superTypes().forEach {
            traverse(it, depth + 1)
        }
    }
    traverse(type, 0)
    return map
}

internal fun IrPluginContext.findClosestCommonSupertype(first: IrType, second: IrType): IrType {
    if (first == second)
        return first

    val depthSupertypesFirst = getAllSupertypes(first)
    val depthSupertypesSecond = getAllSupertypes(second)
    val commonSupertypes = depthSupertypesFirst.keys.intersect(depthSupertypesSecond.keys)

    return commonSupertypes.minByOrNull { superType ->
        max(depthSupertypesFirst[superType]!!, depthSupertypesSecond[superType]!!)
    } ?: if (first.isNullable() || second.isNullable()) irBuiltIns.anyNType else irBuiltIns.anyType
}

internal fun IrPluginContext.findClosestCommonSupertype(types: List<IrType>): IrType =
    if (types.isEmpty()) throw IllegalArgumentException("")
    else if (types.size == 1) types[0]
    else if (types.size == 2) findClosestCommonSupertype(types[0], types[1])
    else findClosestCommonSupertype(types[0], findClosestCommonSupertype(types.subList(1, types.size)))

internal fun IrFunction.isPure(): Boolean {
    var pure = true
    acceptVoid(object: IrVisitorVoid() {
        override fun visitFunction(declaration: IrFunction) {
            if (declaration != this@isPure && !declaration.isPure())
                pure = false
        }

        override fun visitSimpleFunction(declaration: IrSimpleFunction) {
            if (declaration != this@isPure && !declaration.isPure())
                pure = false
        }

        override fun visitExpression(expression: IrExpression) {
            if (!expression.isPure(false))
                pure = false
        }
    })
    return pure
}

internal val IrFunction.isNotStatic: Boolean
    get() = parent is IrClass && dispatchReceiverParameter != null

internal fun IrBuilder.irCheckNotNull(expression: IrExpression): IrCall =
    irCall(context.irBuiltIns.checkNotNullSymbol, type=expression.type.makeNotNull()).apply {
        arguments[0] = expression
        typeArguments[0] = type
    }

internal val IrFunction.hasValueParameters: Boolean
    get() = parameters.any { it.kind == IrParameterKind.Regular }

internal fun IrFunction.getValueParameters(): List<IrValueParameter> =
    parameters.filter { it.kind == IrParameterKind.Regular }

internal fun IrFactory.simpleBlockBody(statements: List<IrStatement>): IrBlockBody =
    createBlockBody(startOffset = UNDEFINED_OFFSET, endOffset = UNDEFINED_OFFSET, statements = statements)

internal inline fun <reified T> IrFunction.hasAnnotation(): Boolean =
    annotations.any {
        it.isAnnotation(FqName(T::class.qualifiedName ?: T::class.java.canonicalName))
    }

internal fun IrBuilder.irCallWithArgs(
    receiver: IrExpression,
    callee: IrSimpleFunctionSymbol,
    vararg args: IrExpression
): IrCall =
    irCall(callee).apply {
        dispatchReceiver = receiver
        callee.owner.getValueParameters().forEachIndexed { index, parameter ->
            arguments[parameter] = args[index]
        }
    }

internal fun IrClassSymbol.typeWithArguments(vararg arguments: IrTypeArgument): IrSimpleType =
    this.typeWithArguments(arguments.toList())
