@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package pt.iscte.ambco.kmemoize.compiler

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
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeWithArguments
import org.jetbrains.kotlin.ir.util.isAnnotation
import org.jetbrains.kotlin.name.FqName

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
