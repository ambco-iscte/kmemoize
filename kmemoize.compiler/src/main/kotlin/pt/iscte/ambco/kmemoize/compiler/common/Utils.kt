@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package pt.iscte.ambco.kmemoize.compiler.common

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.BuiltInOperatorNames
import org.jetbrains.kotlin.ir.builders.IrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeArgument
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.typeWithArguments
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isAnnotation
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.superTypes
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import kotlin.math.max
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties

// ---------------------------------------
// IrFunction Extensions
// ---------------------------------------

@Suppress("UNCHECKED_CAST")
internal fun IrFunction.isBuiltInOperator(): Boolean {
    val irBuiltInOperatorNames: List<String> = BuiltInOperatorNames::class.declaredMemberProperties.mapNotNull {
        (it as? KProperty1<BuiltInOperatorNames, String>)?.call()
    }
    return name.asString() in irBuiltInOperatorNames || name.identifierOrNullIfSpecial in irBuiltInOperatorNames
}

// Workaround for: couldn't find a function through IrPluginContext.referenceFunctions
internal fun IrPluginContext.referenceDeclaredFunctions(id: CallableId): Collection<IrFunctionSymbol> =
    if (id.className == null)
        referenceFunctions(CallableId(id.packageName, id.callableName))
    else
        referenceClass(ClassId(id.packageName, id.className!!.shortName()))?.functions?.filter {
            it.owner.name == id.callableName
        }?.toList() ?: emptyList()

internal fun IrPluginContext.findFunction(
    id: CallableId,
    predicate: (IrFunctionSymbol) -> Boolean = { true }
): IrSimpleFunctionSymbol =
    referenceFunctions(id).firstOrNull(predicate) ?: error("Could not find function: $id")

internal val IrFunction.hasValueParameters: Boolean
    get() = parameters.any { it.kind == IrParameterKind.Regular }

internal fun IrFunction.getValueParameters(): List<IrValueParameter> =
    parameters.filter { it.kind == IrParameterKind.Regular }

internal inline fun <reified T> IrFunction.hasAnnotation(): Boolean =
    annotations.any {
        it.isAnnotation(FqName(T::class.qualifiedName ?: T::class.java.canonicalName))
    }

// ---------------------------------------
// IrDeclaration Extensions
// ---------------------------------------

internal fun IrDeclaration.declaredWithin(function: IrFunction): Boolean =
    getSuperDeclarations().filterIsInstance<IrFunction>().toSet().singleOrNull() == function

internal fun IrDeclaration.findClosestDeclarationContainer(): IrDeclarationContainer? {
    var current: IrDeclarationParent = this.parent
    while (current !is IrDeclarationContainer)
        current = (current as? IrDeclaration)?.parent ?: return null
    return current
}

internal fun IrDeclaration.getSuperDeclarations(): Sequence<IrDeclaration> =
    sequence {
        var current: IrDeclarationParent? = this@getSuperDeclarations.parent
        while (current != null) {
            (current as? IrDeclaration)?.let { yield(it) }
            current = (current as? IrDeclaration)?.parent
        }
    }

// ---------------------------------------
// IrType Extensions
// ---------------------------------------

internal fun IrPluginContext.findClosestCommonSupertype(first: IrType, second: IrType): IrType {
    if (first == second)
        return first

    fun getSupertypesWithDistanceToBase(type: IrType): Map<IrType, Int> {
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

    val depthSupertypesFirst = getSupertypesWithDistanceToBase(first)
    val depthSupertypesSecond = getSupertypesWithDistanceToBase(second)
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

// ---------------------------------------
// Misc. but Useful
// ---------------------------------------

internal fun IrClassSymbol.typeWithArguments(vararg arguments: IrTypeArgument): IrSimpleType =
    this.typeWithArguments(arguments.toList())

internal fun IrBuilder.irCheckNotNull(expression: IrExpression): IrCall =
    irCall(context.irBuiltIns.checkNotNullSymbol, type=expression.type.makeNotNull()).apply {
        arguments[0] = expression
        typeArguments[0] = type
    }