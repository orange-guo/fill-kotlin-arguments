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

class FillEmptyValueQuickFix(private val suggestArguments: KtValueArgumentList) : LocalQuickFix {

	override fun getFamilyName(): @IntentionFamilyName String = "Fill empty values"

	override fun applyFix(
		project: Project, descriptor: ProblemDescriptor
	) {
		val argumentList = descriptor.psiElement as? KtValueArgumentList ?: return

		val editor = argumentList.findExistingEditor() ?: ImaginaryEditor(
			project, argumentList.containingFile.viewProvider.document
		)

		val factory = KtPsiFactory(project)

		suggestArguments.arguments.forEach { suggestArgument ->
			val contains = argumentList.arguments.any { argument -> argument.getArgumentName()?.asName == suggestArgument.getArgumentName()?.asName }
			if (!contains) {
				argumentList.addArgument(suggestArgument)
			}
		}

		val argumentSize = argumentList.arguments.size

		val putArgumentsOnSeparateLines = true
		// 1. Put arguments on separate lines
		if (putArgumentsOnSeparateLines) {
			PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
			if (argumentList.arguments.isNotEmpty()) {
				PutArgumentOnSeparateLineHelper.applyTo(argumentList, editor)
			}
			argumentList.findElementsInArgsByType<KtValueArgumentList>(argumentSize)
				.filter { it.arguments.isNotEmpty() }.forEach { PutArgumentOnSeparateLineHelper.applyTo(it, editor) }
		}

		val withTrailingComma = false
		// 2. Add trailing commas
		if (withTrailingComma) {
			argumentList.addTrailingCommaIfNeeded(factory)
			argumentList.findElementsInArgsByType<KtValueArgumentList>(argumentSize)
				.forEach { it.addTrailingCommaIfNeeded(factory) }
		}

		// 3. Remove full qualifiers and import references
		// This should be run after PutArgumentOnSeparateLineHelper
		argumentList.findElementsInArgsByType<KtQualifiedExpression>(argumentSize)
			.forEach { ShortenReferences.DEFAULT.process(it) }
		argumentList.findElementsInArgsByType<KtLambdaExpression>(argumentSize)
			.forEach { ShortenReferences.DEFAULT.process(it) }

		val movePointerToEveryArgument = true
		// 4. Set argument placeholders
		// This should be run on final state
		if (editor !is ImaginaryEditor && movePointerToEveryArgument) {
			PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
			argumentList.startToReplaceArguments(argumentSize, editor)
		}
	}

	private fun KtValueArgumentList.startToReplaceArguments(startIndex: Int, editor: Editor) {
		val templateBuilder = TemplateBuilderImpl(this)
		arguments.drop(startIndex).forEach { argument ->
			val argumentExpression = argument.argumentExpression
			if (argumentExpression != null) {
				templateBuilder.replaceElement(argumentExpression, argumentExpression.text)
			} else {
				val commaOffset = if (argument.text.lastOrNull() == ',') 1 else 0
				val endOffset = argument.textRangeIn(this).endOffset - commaOffset
				templateBuilder.replaceRange(TextRange(endOffset, endOffset), "")
			}
		}
		templateBuilder.run(editor, true)
	}

	private fun KtValueArgumentList.addTrailingCommaIfNeeded(factory: KtPsiFactory) {
		if (this.arguments.isNotEmpty() && !this.hasTrailingComma()) {
			val comma = factory.createComma()
			this.addAfter(comma, this.arguments.last())
		}
	}

	private fun KtValueArgumentList.hasTrailingComma() =
		rightParenthesis?.getPrevSiblingIgnoringWhitespaceAndComments(withItself = false)?.node?.elementType == KtTokens.COMMA

	private inline fun <reified T : KtElement> KtValueArgumentList.findElementsInArgsByType(argStartOffset: Int): List<T> {
		return this.arguments.subList(argStartOffset, this.arguments.size).flatMap { argument ->
			argument.collectDescendantsOfType<T>()
		}
	}

	object PutArgumentOnSeparateLineHelper {

		private val intentionClass: Class<*>? by lazy {
			try {
				Class.forName("org.jetbrains.kotlin.idea.intentions.ChopArgumentListIntention")
			} catch (e: ClassNotFoundException) {
				try {
					Class.forName("org.jetbrains.kotlin.idea.codeInsight.intentions.shared.ChopArgumentListIntention")
				} catch (e: ClassNotFoundException) {
					null
				}
			}
		}

		fun isAvailable(): Boolean = intentionClass != null

		fun applyTo(element: KtValueArgumentList, editor: Editor?) {
			val clazz = intentionClass ?: return
			val constructor = clazz.getConstructor()
			val intention = constructor.newInstance()
			val method = clazz.getMethod("applyTo", KtElement::class.java, Editor::class.java)
			method.invoke(intention, element, editor)
		}
	}

}