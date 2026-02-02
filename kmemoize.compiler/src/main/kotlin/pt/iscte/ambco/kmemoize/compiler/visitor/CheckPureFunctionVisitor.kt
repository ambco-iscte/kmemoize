package pt.iscte.ambco.kmemoize.compiler.visitor

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationBase
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrExternalPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrLocalDelegatedProperty
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrReplSnippet
import org.jetbrains.kotlin.ir.declarations.IrScript
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeAlias
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueDeclaration
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrBranch
import org.jetbrains.kotlin.ir.expressions.IrBreak
import org.jetbrains.kotlin.ir.expressions.IrBreakContinue
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrCallableReference
import org.jetbrains.kotlin.ir.expressions.IrCatch
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrComposite
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstantArray
import org.jetbrains.kotlin.ir.expressions.IrConstantObject
import org.jetbrains.kotlin.ir.expressions.IrConstantPrimitive
import org.jetbrains.kotlin.ir.expressions.IrConstantValue
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrContainerExpression
import org.jetbrains.kotlin.ir.expressions.IrContinue
import org.jetbrains.kotlin.ir.expressions.IrDeclarationReference
import org.jetbrains.kotlin.ir.expressions.IrDelegatingConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrDynamicMemberExpression
import org.jetbrains.kotlin.ir.expressions.IrDynamicOperatorExpression
import org.jetbrains.kotlin.ir.expressions.IrElseBranch
import org.jetbrains.kotlin.ir.expressions.IrEnumConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrErrorCallExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFieldAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetClass
import org.jetbrains.kotlin.ir.expressions.IrGetEnumValue
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrInlinedFunctionBlock
import org.jetbrains.kotlin.ir.expressions.IrInstanceInitializerCall
import org.jetbrains.kotlin.ir.expressions.IrLocalDelegatedPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrLoop
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrRawFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrReturnableBlock
import org.jetbrains.kotlin.ir.expressions.IrRichCallableReference
import org.jetbrains.kotlin.ir.expressions.IrRichFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrRichPropertyReference
import org.jetbrains.kotlin.ir.expressions.IrSetField
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrStringConcatenation
import org.jetbrains.kotlin.ir.expressions.IrSuspendableExpression
import org.jetbrains.kotlin.ir.expressions.IrSuspensionPoint
import org.jetbrains.kotlin.ir.expressions.IrSyntheticBody
import org.jetbrains.kotlin.ir.expressions.IrThrow
import org.jetbrains.kotlin.ir.expressions.IrTry
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrValueAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.invokeFunction
import org.jetbrains.kotlin.ir.util.isAssignable
import org.jetbrains.kotlin.ir.util.isImmutable
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrVisitor
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.Logger
import org.jetbrains.kotlin.util.WithLogger
import pt.iscte.ambco.kmemoize.api.UnsafeMemoize
import pt.iscte.ambco.kmemoize.compiler.common.declaredWithin
import pt.iscte.ambco.kmemoize.compiler.common.hasAnnotation
import pt.iscte.ambco.kmemoize.compiler.common.isBuiltInOperator
import pt.iscte.ambco.kmemoize.compiler.common.referenceDeclaredFunctions

internal fun IrPluginContext.isPure(function: IrFunction, logger: Logger): Boolean =
    function.accept(CheckPureFunctionVisitor(this, function, logger), false)

@OptIn(UnsafeDuringIrConstructionAPI::class)
data class CheckPureFunctionVisitor(
    private val context: IrPluginContext,
    private val function: IrFunction,
    override val logger: Logger
): IrVisitor<Boolean, Boolean>(), WithLogger {

    private val knownImpureDeclarations: List<IrDeclaration> = (listOfNotNull(
        context.referenceClass(
            ClassId(
                FqName("java.util"),
                Name.identifier("Random")
            )
        ),
        context.referenceClass(
            ClassId(
                FqName("kotlin"),
                Name.identifier("Random")
            )
        )
    ) +
    context.referenceFunctions(
        CallableId(
            FqName("kotlin.random"),
            Name.identifier("Random")
        )
    ) +
    context.referenceDeclaredFunctions(
        CallableId(
            FqName("java.lang"),
            FqName("Math"),
            Name.identifier("random")
        )
    )).mapNotNull { it.owner as? IrDeclaration }

    private val visited = mutableMapOf<IrDeclaration, Boolean>()

    private fun isLocalOrFinal(declaration: IrDeclaration): Boolean =
        declaration.declaredWithin(function) || when (declaration) {
            is IrField -> declaration.isFinal
            is IrProperty -> !declaration.isVar
            is IrValueParameter -> declaration.isImmutable || !declaration.isAssignable
            is IrVariable -> !declaration.isVar
            is IrValueDeclaration -> declaration.isImmutable || !declaration.isAssignable
            else -> false
        }

    override fun visitElement(element: IrElement, data: Boolean): Boolean =
        element !in knownImpureDeclarations

    // --------------------------------
    // VARS, FIELDS, PROPERTIES, ETC.
    // --------------------------------

    override fun visitField(declaration: IrField, data: Boolean): Boolean =
        isLocalOrFinal(declaration) && declaration.initializer?.accept(this, data) ?: true

    override fun visitDeclaration(declaration: IrDeclarationBase, data: Boolean): Boolean =
        isLocalOrFinal(declaration)

    override fun visitProperty(declaration: IrProperty, data: Boolean): Boolean =
        isLocalOrFinal(declaration) && declaration.backingField?.accept(this, data) ?: true

    override fun visitVariable(declaration: IrVariable, data: Boolean): Boolean =
        isLocalOrFinal(declaration) && declaration.initializer?.accept(this, data) ?: true

    override fun visitValueParameter(declaration: IrValueParameter, data: Boolean): Boolean =
        (declaration.defaultValue?.accept(this, data) ?: true) ||
        (declaration !in function.parameters && isLocalOrFinal(declaration))

    // --------------------------------
    // ALWAYS PURE BY DEFINITION
    // --------------------------------

    override fun visitBreak(jump: IrBreak, data: Boolean): Boolean = true

    override fun visitBreakContinue(jump: IrBreakContinue, data: Boolean): Boolean = true

    override fun visitContinue(jump: IrContinue, data: Boolean): Boolean = true

    override fun visitConst(expression: IrConst, data: Boolean): Boolean = true

    override fun visitConstantValue(expression: IrConstantValue, data: Boolean): Boolean = true

    override fun visitSyntheticBody(body: IrSyntheticBody, data: Boolean): Boolean = true

    override fun visitTypeAlias(declaration: IrTypeAlias, data: Boolean): Boolean = true

    override fun visitTypeParameter(declaration: IrTypeParameter, data: Boolean): Boolean = true

    // --------------------------------
    // CHECK FOR KNOWN IMPURE BUILT-INS
    // --------------------------------

    override fun visitFunction(declaration: IrFunction, data: Boolean): Boolean {
        if (declaration !in visited) {
            visited[declaration] = if (declaration.isBuiltInOperator())
                true
            else if (declaration in knownImpureDeclarations)
                false
            else
                declaration.parameters.all { it.accept(this, data) } &&
                declaration.body?.accept(this, data) ?: true
        }
        return visited[declaration]!!
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Boolean): Boolean {
        if (declaration !in visited) {
            visited[declaration] = if (declaration.isBuiltInOperator())
                true
            else if (declaration in knownImpureDeclarations)
                false
            else
                declaration.parameters.all { it.accept(this, data) } &&
                declaration.body?.accept(this, data) ?: true
        }
        return visited[declaration]!!
    }

    override fun visitClass(declaration: IrClass, data: Boolean): Boolean {
        if (declaration !in visited) {
            visited[declaration] = if (declaration in knownImpureDeclarations)
                false
            else
                declaration.declarations.all { it.accept(this, data) } &&
                declaration.superTypes.all { it.classOrNull?.owner?.accept(this, data) ?: true }
        }
        return visited[declaration]!!
    }

    // --------------------------------
    // TRAVERSE IR TREE RECURSIVELY
    // --------------------------------

    override fun visitCall(expression: IrCall, data: Boolean): Boolean =
        if (expression.symbol.owner == function)
            true
        else
            expression.symbol.owner.accept(this, data) &&
            expression.arguments.all { it?.accept(this, data) ?: true }

    override fun visitLoop(loop: IrLoop, data: Boolean): Boolean =
        loop.condition.accept(this, data) && loop.body?.accept(this, data) ?: true

    override fun visitFieldAccess(expression: IrFieldAccessExpression, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data) && expression.receiver?.accept(this, data) ?: true

    override fun visitValueAccess(expression: IrValueAccessExpression, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data)

    override fun visitMemberAccess(expression: IrMemberAccessExpression<*>, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data) &&
        expression.arguments.all { it?.accept(this, data) ?: true }

    override fun visitFunctionAccess(expression: IrFunctionAccessExpression, data: Boolean): Boolean =
        if (expression.symbol.owner == function) true
        else expression.symbol.owner.accept(this, data)

    override fun visitDynamicMemberExpression(expression: IrDynamicMemberExpression, data: Boolean): Boolean =
        expression.receiver.accept(this, data)

    override fun visitDynamicOperatorExpression(expression: IrDynamicOperatorExpression, data: Boolean): Boolean =
        expression.receiver.accept(this, data) && expression.arguments.all { it.accept(this, data) }

    override fun visitBlock(expression: IrBlock, data: Boolean): Boolean =
        expression.statements.all { it.accept(this, data) }

    override fun visitBody(body: IrBody, data: Boolean): Boolean =
        body.statements.all { it.accept(this, data) }

    override fun visitExpressionBody(body: IrExpressionBody, data: Boolean): Boolean =
        body.expression.accept(this, data) && body.statements.all { it.accept(this, data) }

    override fun visitBlockBody(body: IrBlockBody, data: Boolean): Boolean =
        body.statements.all { it.accept(this, data) }

    override fun visitDeclarationReference(expression: IrDeclarationReference, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data)

    override fun visitGetField(expression: IrGetField, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data) && (expression.receiver?.accept(this, data) ?: true)

    override fun visitSetField(expression: IrSetField, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data) && expression.value.accept(this, data)

    override fun visitFunctionExpression(expression: IrFunctionExpression, data: Boolean): Boolean =
        expression.function.accept(this, data)

    override fun visitWhileLoop(loop: IrWhileLoop, data: Boolean): Boolean =
        loop.condition.accept(this, data) && (loop.body?.accept(this, data) ?: true)

    override fun visitDoWhileLoop(loop: IrDoWhileLoop, data: Boolean): Boolean =
        loop.condition.accept(this, data) && (loop.body?.accept(this, data) ?: true)

    override fun visitReturn(expression: IrReturn, data: Boolean): Boolean =
        expression.value.accept(this, true)

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: Boolean): Boolean =
        expression.arguments.all { it.accept(this, data) }

    override fun visitSuspensionPoint(expression: IrSuspensionPoint, data: Boolean): Boolean =
        expression.suspensionPointIdParameter.accept(this, data) &&
        expression.result.accept(this, data) && expression.resumeResult.accept(this, data)

    override fun visitSuspendableExpression(expression: IrSuspendableExpression, data: Boolean): Boolean =
        expression.suspensionPointId.accept(this, data) &&
        expression.result.accept(this, data)

    override fun visitThrow(expression: IrThrow, data: Boolean): Boolean =
        expression.value.accept(this, true)

    override fun visitTry(aTry: IrTry, data: Boolean): Boolean =
        aTry.tryResult.accept(this, data) && aTry.catches.all { it.accept(this, data) } &&
        (aTry.finallyExpression?.accept(this, data) ?: true)

    override fun visitCatch(aCatch: IrCatch, data: Boolean): Boolean =
        aCatch.catchParameter.accept(this, data) && aCatch.result.accept(this, data)

    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: Boolean): Boolean =
        expression.argument.accept(this, data)

    override fun visitGetValue(expression: IrGetValue, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data)

    override fun visitSetValue(expression: IrSetValue, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data) && expression.value.accept(this, true)

    override fun visitVararg(expression: IrVararg, data: Boolean): Boolean =
        expression.elements.all { it.accept(this, data) }

    override fun visitSpreadElement(spread: IrSpreadElement, data: Boolean): Boolean =
        spread.expression.accept(this, data)

    override fun visitWhen(expression: IrWhen, data: Boolean): Boolean =
        expression.branches.all { it.accept(this, data) }

    override fun visitBranch(branch: IrBranch, data: Boolean): Boolean =
        branch.condition.accept(this, data) && branch.result.accept(this, data)

    override fun visitElseBranch(branch: IrElseBranch, data: Boolean): Boolean =
        branch.condition.accept(this, data) && branch.result.accept(this, data)

    override fun visitRawFunctionReference(expression: IrRawFunctionReference, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data)

    override fun visitContainerExpression(expression: IrContainerExpression, data: Boolean): Boolean =
        expression.statements.all { it.accept(this, data) }

    override fun visitCallableReference(expression: IrCallableReference<*>, data: Boolean): Boolean =
        expression.arguments.all { it?.accept(this, data) ?: true } &&
        expression.dispatchReceiver?.accept(this, data) ?: true

    override fun visitFunctionReference(expression: IrFunctionReference, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data) &&
        expression.arguments.all { it?.accept(this, data) ?: true } &&
        expression.dispatchReceiver?.accept(this, data) ?: true

    override fun visitPropertyReference(expression: IrPropertyReference, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data) &&
        expression.dispatchReceiver?.accept(this, data) ?: true

    override fun visitLocalDelegatedPropertyReference(expression: IrLocalDelegatedPropertyReference, data: Boolean): Boolean =
        expression.delegate.owner.accept(this, data) &&
        expression.arguments.all { it?.accept(this, data) ?: true }

    override fun visitRichCallableReference(expression: IrRichCallableReference<*>, data: Boolean): Boolean =
        expression.boundValues.all { it.accept(this, data) } &&
        expression.invokeFunction.accept(this, data)

    override fun visitRichFunctionReference(expression: IrRichFunctionReference, data: Boolean): Boolean =
        expression.invokeFunction.accept(this, data) &&
        expression.boundValues.all { it.accept(this, data) }

    override fun visitRichPropertyReference(expression: IrRichPropertyReference, data: Boolean): Boolean =
        expression.boundValues.all { it.accept(this,data) } &&
        expression.getterFunction.accept(this, data) &&
        expression.setterFunction?.accept(this, data) ?: true

    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data) &&
        expression.arguments.all { it?.accept(this, data) ?: true } &&
        expression.dispatchReceiver?.accept(this, data) ?: true

    override fun visitEnumConstructorCall(expression: IrEnumConstructorCall, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data) &&
        expression.arguments.all { it?.accept(this, data) ?: true } &&
        expression.dispatchReceiver?.accept(this, data) ?: true

    override fun visitErrorCallExpression(expression: IrErrorCallExpression, data: Boolean): Boolean =
        expression.arguments.all { it.accept(this, data) } &&
        expression.explicitReceiver?.accept(this, data) ?: true

    override fun visitComposite(expression: IrComposite, data: Boolean): Boolean =
        expression.statements.all { it.accept(this, data) }

    override fun visitReturnableBlock(expression: IrReturnableBlock, data: Boolean): Boolean =
        expression.statements.all { it.accept(this, data) }

    override fun visitInlinedFunctionBlock(inlinedBlock: IrInlinedFunctionBlock, data: Boolean): Boolean =
        inlinedBlock.statements.all { it.accept(this, data) } &&
        inlinedBlock.inlinedFunctionSymbol?.owner?.accept(this, data) ?: true

    override fun visitAnonymousInitializer(declaration: IrAnonymousInitializer, data: Boolean): Boolean =
        declaration.body.accept(this, data)

    override fun visitConstructor(declaration: IrConstructor, data: Boolean): Boolean =
        declaration.body?.accept(this, data) ?: true

    override fun visitLocalDelegatedProperty(declaration: IrLocalDelegatedProperty, data: Boolean): Boolean =
        declaration.delegate.accept(this, data)

    override fun visitModuleFragment(declaration: IrModuleFragment, data: Boolean): Boolean =
        declaration.files.all { it.accept(this, data) }

    override fun visitPackageFragment(declaration: IrPackageFragment, data: Boolean): Boolean =
        declaration.declarations.all { it.accept(this, data) }

    override fun visitExternalPackageFragment(declaration: IrExternalPackageFragment, data: Boolean): Boolean =
        declaration.declarations.all { it.accept(this, data) }

    override fun visitFile(declaration: IrFile, data: Boolean): Boolean =
        declaration.declarations.all { it.accept(this, data) }

    override fun visitReplSnippet(declaration: IrReplSnippet, data: Boolean): Boolean =
        declaration.body.accept(this, data)

    override fun visitScript(declaration: IrScript, data: Boolean): Boolean =
        declaration.statements.all { it.accept(this, data) } // TODO rest of the fields

    override fun visitClassReference(expression: IrClassReference, data: Boolean): Boolean =
        expression.classType.classOrNull?.owner?.accept(this, data) ?: true

    override fun visitConstantArray(expression: IrConstantArray, data: Boolean): Boolean =
        expression.elements.all { it.accept(this, data) }

    override fun visitConstantObject(expression: IrConstantObject, data: Boolean): Boolean =
        expression.valueArguments.all { it.accept(this, data) }

    override fun visitConstantPrimitive(expression: IrConstantPrimitive, data: Boolean): Boolean =
        expression.value.accept(this, data)

    override fun visitConstructorCall(expression: IrConstructorCall, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data) &&
        expression.arguments.all { it?.accept(this, data) ?: true }

    override fun visitEnumEntry(declaration: IrEnumEntry, data: Boolean): Boolean =
        declaration.initializerExpression?.accept(this, data) ?: true &&
        declaration.correspondingClass?.accept(this, data) ?: true

    override fun visitGetClass(expression: IrGetClass, data: Boolean): Boolean =
        expression.argument.accept(this, data)

    override fun visitGetEnumValue(expression: IrGetEnumValue, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data)

    override fun visitGetObjectValue(expression: IrGetObjectValue, data: Boolean): Boolean =
        expression.symbol.owner.accept(this, data)

    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: Boolean): Boolean =
        expression.classSymbol.owner.accept(this, data)
}