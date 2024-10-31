package com.github.orangeguo.fillkotlinarguments

import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments

/**
 * The default implementation of [AclManager]
 *
 * @author Xiangcheng Kuo
 * @since 2024-10-30
 */
class SpecifyAllRemainingArgumentsQuickFix(
	private val suggestArguments: KtValueArgumentList
) : LocalQuickFix {

	override fun getFamilyName(): @IntentionFamilyName String = "Specify all remaining arguments"

	override fun applyFix(
		project: Project, descriptor: ProblemDescriptor
	) {
		val argumentList = descriptor.psiElement as? KtValueArgumentList ?: return

		val editor = argumentList.findExistingEditor() ?: ImaginaryEditor(
			project, argumentList.containingFile.viewProvider.document
		)

		val factory = KtPsiFactory(project)

		suggestArguments.arguments.forEach { suggestArgument ->
			val contains =
				argumentList.arguments.any { argument -> argument.getArgumentName()?.asName == suggestArgument.getArgumentName()?.asName }
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