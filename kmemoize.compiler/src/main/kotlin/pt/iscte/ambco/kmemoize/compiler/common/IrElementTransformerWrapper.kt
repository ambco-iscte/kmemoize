package pt.iscte.ambco.kmemoize.compiler.common

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.linkage.IrDeserializer
import org.jetbrains.kotlin.backend.common.serialization.kind
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.hasTopLevelEqualFqName
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.Logger
import org.jetbrains.kotlin.util.WithLogger

open class IrElementTransformerWrapper(
    protected val context: IrPluginContext,
    override val logger: Logger
): IrElementTransformerVoidWithContext(), WithLogger {

    protected fun findFunction(
        id: CallableId,
        predicate: (IrFunctionSymbol) -> Boolean = { true }
    ): IrSimpleFunctionSymbol =
        context.referenceFunctions(id).firstOrNull(predicate) ?: error("Could not find function: $id")

    protected fun findFunction(
        packageName: String,
        className: String,
        functionName: String,
        predicate: (IrFunctionSymbol) -> Boolean = { true }
    ): IrSimpleFunctionSymbol =
        findFunction(
            CallableId(
                FqName(packageName),
                FqName(className),
                Name.identifier(functionName)
            ),
            predicate
        )

    protected fun findFunction(
        packageName: String,
        functionName: String,
        predicate: (IrFunctionSymbol) -> Boolean = { true }
    ): IrSimpleFunctionSymbol =
        findFunction(
            CallableId(
                FqName(packageName),
                Name.identifier(functionName)
            ),
            predicate
        )

    protected fun findClass(id: ClassId): IrClassSymbol =
        context.referenceClass(id) ?: error("Could not find class: $id")

    protected fun findClass(packageName: String, className: String): IrClassSymbol =
        findClass(ClassId(
            FqName(packageName),
            Name.identifier(className)
        ))
}