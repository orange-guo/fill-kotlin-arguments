package com.github.orangeguo.fillkotlinarguments

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getSymbolContainingMemberDeclarations
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.isEnum
import org.jetbrains.kotlin.idea.codeinsight.utils.isNullableAnyType
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.valueArgumentListVisitor

/**
 * @author Xiangcheng Kuo
 * @since 2024-10-30
 */
class FillAllRemainingArgumentsKotlinInspection : AbstractKotlinInspection() {

	data class Context(
		// A list of functions that are currently being processed
		// If the function is recursive, we don't want to fill values again
		val processingFunctions: List<String>,
		val factory: KtPsiFactory,
		val session: KaSession,
	)

	fun Context.withNewProcessingFunction(function: String): Context =
		copy(processingFunctions = processingFunctions + function)

	@OptIn(KaIdeApi::class)
	override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
		valueArgumentListVisitor { argumentList ->
			// try to fill empty values // functionSymbol.importableFqName
			analyze(argumentList) {

				val callExpression = argumentList.parent as? KtCallExpression ?: return@analyze
				val functionSymbol =
					callExpression.calleeExpression?.mainReference?.resolveToSymbol() as? KaFunctionSymbol
						?: return@analyze

				if (functionSymbol.valueParameters.isEmpty()) {
					return@analyze
				}
				if (functionSymbol.valueParameters.size == argumentList.arguments.size) {
					return@analyze
				}

				val context = Context(
					processingFunctions = listOf(functionSymbol.importableFqName!!.asString()),
					factory = KtPsiFactory(holder.project),
					session = this,
				)

				val text = functionSymbol.createArgumentList(context)

				holder.registerProblem(
					argumentList,
					"Fill all remaining arguments",
					FillAllRemainingArgumentsQuickFix(context.factory.createCallArguments(text))
				)
			}

		}

	fun KaType.createValue(ctx: Context): String = with(ctx.session) {
		when {
			isBooleanType -> "false"

			isCharType -> "''"
			isCharSequenceType || isStringType -> """"""""

			isDoubleType -> "0.0"
			isFloatType -> "0.0f"

			isIntType || isLongType || isShortType -> "0"

			isArrayOrPrimitiveArray -> "emptyArray()"

			isEnum() -> createEnumValue(ctx)

			isFunctionType -> (this@createValue as KaFunctionType).createFunctionValue()
			isNullableAnyType() -> "null"
			else -> createConstructorCall(ctx)
		}
	}

	@OptIn(KaIdeApi::class)
	private fun KaType.createConstructorCall(ctx: Context): String = with(ctx.session) {
		val functionSymbol: KaFunctionSymbol =
			symbol?.getSymbolContainingMemberDeclarations()?.declaredMemberScope?.constructors?.filter { it.isPrimary }
				?.firstOrNull() ?: return "TODO()"
		functionSymbol.createFunctionCall(ctx)
	}

	@OptIn(KaIdeApi::class)
	private fun KaFunctionSymbol.createFunctionCall(ctx: Context): String = with(ctx.session) {
		val functionName = importableFqName?.asString() ?: return "TODO()"
		val newCtx = ctx.withNewProcessingFunction(functionName)
		if (functionName in ctx.processingFunctions) {
			return """TODO("skip recursive")"""
		}
		val arguments = createArgumentList(newCtx)
		"$functionName$arguments"
	}


	// for example:
	// (name = "name", age = 12)
	@OptIn(KaIdeApi::class)
	private fun KaFunctionSymbol.createArgumentList(ctx: Context): String = with(ctx.session) {
		val functionName = importableFqName?.asString() ?: return "TODO()"
		val newCtx = ctx.withNewProcessingFunction(functionName)

		valueParameters.joinToString(prefix = "(", separator = ",", postfix = ")") { symbol ->
			symbol.name.asString() + " = " + symbol.returnType.createValue(newCtx)
		}
	}


	context(KaSession)
	private fun KaFunctionType.createFunctionValue(): String = buildString {
		val validator = CollectingNameValidator()
		append("{")
		if (parameterTypes.size > 1) {
			val lambdaParameters = parameterTypes.joinToString(separator = ", ", postfix = "->") {
				val name = KotlinNameSuggester.suggestNameByValidIdentifierName(
					it.symbol!!.classId!!.shortClassName.asString(), validator, true
				)!!
				validator.addName(name)
				val typeText = it.symbol!!.classId!!.asFqNameString()
				val nullable = if (isNullableAnyType()) "?" else ""
				"$name: $typeText$nullable"

			}
			append(lambdaParameters)
		}

		append("""TODO("Implement Me")""")

		append("}")
	}

	private fun KaType.createEnumValue(ctx: Context): String = with(ctx.session) {
		val first =
			symbol?.getSymbolContainingMemberDeclarations()?.declaredMemberScope?.getAllPossibleNames()?.firstOrNull()

		return when (first) {
			null -> "TODO()"
			else -> return symbol?.classId?.asSingleFqName()?.child(first)?.asString()!!
		}
	}

}

