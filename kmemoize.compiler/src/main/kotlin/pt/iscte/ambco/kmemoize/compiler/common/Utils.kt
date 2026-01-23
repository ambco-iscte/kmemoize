@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package pt.iscte.ambco.kmemoize.compiler.common

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.utils.isDispatchReceiver
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrFactory
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.createBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.typeWithArguments
import org.jetbrains.kotlin.ir.util.isAnnotation
import org.jetbrains.kotlin.name.FqName

internal val IrFunction.hasValueParameters: Boolean
    get() = parameters.any { !it.isDispatchReceiver }

internal fun IrFunction.getValueParameters(): List<IrValueParameter> =
    parameters.filter { it.kind == IrParameterKind.Regular }

internal fun IrFactory.simpleBlockBody(statements: List<IrStatement>): IrBlockBody =
    createBlockBody(startOffset = UNDEFINED_OFFSET, endOffset = UNDEFINED_OFFSET, statements = statements)

internal fun IrFactory.simpleBlockBody(vararg statements: IrStatement): IrBlockBody =
    simpleBlockBody(statements.toList())

internal inline fun <reified T> IrFunction.hasAnnotation(): Boolean =
    annotations.any {
        it.isAnnotation(FqName(T::class.qualifiedName ?: T::class.java.canonicalName))
    }

internal fun IrBuilder.irCallWithArgs(receiver: IrExpression, callee: IrSimpleFunctionSymbol, vararg args: IrExpression): IrCall =
    irCall(callee).apply {
        dispatchReceiver = receiver
        callee.owner.parameters.filter { !it.isDispatchReceiver }.forEachIndexed { index, parameter ->
            arguments[parameter] = args[index]
        }
    }

internal fun IrBuilder.irCallWithArgs(receiver: IrExpression, callee: IrFunction, vararg args: IrExpression): IrFunctionAccessExpression =
    irCall(callee).apply {
        dispatchReceiver = receiver
        callee.parameters.filter { !it.isDispatchReceiver }.forEachIndexed { index, parameter ->
            arguments[parameter] = args[index]
        }
    }

internal fun IrBuilder.irCallWithArgs(callee: IrSimpleFunctionSymbol, vararg args: IrExpression): IrCall =
    irCall(callee).apply {
        callee.owner.parameters.forEachIndexed { index, parameter ->
            arguments[index] = args[index]
        }
    }

internal fun IrBuilder.irCallWithArgs(callee: IrFunction, vararg args: IrExpression): IrFunctionAccessExpression =
    irCall(callee).apply {
        callee.parameters.forEachIndexed { index, parameter ->
            arguments[index] = args[index]
        }
    }

internal inline fun <T, reified R> Iterable<T>.mapToArray(transform: (T) -> R): Array<R> =
    map(transform).toTypedArray()

internal fun IrClassSymbol.typeWithArguments(vararg arguments: IrTypeArgument): IrSimpleType =
    this.typeWithArguments(arguments.toList())
