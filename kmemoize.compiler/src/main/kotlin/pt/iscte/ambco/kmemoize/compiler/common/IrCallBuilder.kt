package pt.iscte.ambco.kmemoize.compiler.common

import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI

internal fun IrBuilder.irCallBuilder(callee: IrSimpleFunctionSymbol): IrCallBuilder =
    IrCallBuilder(this, callee)

@OptIn(UnsafeDuringIrConstructionAPI::class)
class IrCallBuilder(private val builder: IrBuilder, private val callee: IrSimpleFunctionSymbol) {
    private var receiver: IrExpression? = null
    private val args = mutableListOf<IrExpression>()

    fun withReceiver(receiver: IrExpression): IrCallBuilder {
        this.receiver = receiver
        return this
    }

    fun withArguments(vararg arguments: IrExpression): IrCallBuilder {
        require(arguments.size == callee.owner.getValueParameters().size) {
            "Function ${callee.owner.name} takes ${callee.owner.getValueParameters().size} arguments, but " +
                    "${arguments.size} were provided"
        }
        args.clear()
        args.addAll(arguments)
        return this
    }

    fun build(): IrCall =
        builder.irCall(callee).apply {
            dispatchReceiver = receiver
            callee.owner.getValueParameters().forEachIndexed { index, parameter ->
                arguments[parameter] = args[index]
            }
        }
}