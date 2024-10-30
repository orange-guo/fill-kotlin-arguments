package com.github.orangeguo.fillkotlinarguments

import com.appmattus.kotlinfixture.kotlinFixture
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
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
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.codeinsight.utils.isEnum
import org.jetbrains.kotlin.idea.codeinsight.utils.isNullableAnyType
import org.jetbrains.kotlin.idea.core.CollectingNameValidator
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.valueArgumentListVisitor

class SpecifyAllRemainingArgumentsKotlinInspection : AbstractKotlinInspection() {

	data class Context(
		// A list of functions that are currently being processed
		// If the function is recursive, we don't want to fill values again
		val processingFunctions: List<String>,
		val factory: KtPsiFactory,
		val session: KaSession,
	)

	fun Context.withProcessingFunction(function: String): Context =
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

				holder.registerProblem(argumentList, "Fill empty values", FillEmptyValueQuickFix(context.factory.createCallArguments(text)))
			}

		}

	val fixture = kotlinFixture()

	fun KaType.createValue(ctx: Context): String = with(ctx.session) {
		when {
			isBooleanType -> "false"

			isCharType -> "''"
			isCharSequenceType || isStringType -> """"${fixture.create(String::class.java)}""""

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
				?.first() ?: return "TODO()"
		functionSymbol.createFunctionCall(ctx)
	}

	@OptIn(KaIdeApi::class)
	private fun KaFunctionSymbol.createFunctionCall(ctx: Context): String = with(ctx.session) {
		val functionName = importableFqName?.asString() ?: return "TODO()"
		val newCtx = ctx.withProcessingFunction(functionName)
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
		val newCtx = ctx.withProcessingFunction(functionName)

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

