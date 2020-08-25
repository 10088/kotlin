/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.refactoring.isAbstract
import org.jetbrains.kotlin.idea.util.isExpectDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall

class KotlinInlineFunctionHandler : KotlinInlineActionHandler() {
    override val helpId: String? get() = HelpID.INLINE_METHOD

    override val refactoringName: String get() = KotlinBundle.message("title.inline.function")

    override fun canInlineKotlinElement(element: KtElement): Boolean = element is KtNamedFunction

    override fun inlineKotlinElement(project: Project, editor: Editor?, element: KtElement) {
        element as KtNamedFunction
        if (!checkSources(project, editor, element)) return

        if (!element.hasBody()) {
            val message = when {
                element.isAbstract() -> KotlinBundle.message("refactoring.cannot.be.applied.to.abstract.declaration", refactoringName)
                element.isExpectDeclaration() -> KotlinBundle.message(
                    "refactoring.cannot.be.applied.to.expect.declaration",
                    refactoringName
                )
                else -> KotlinBundle.message("refactoring.cannot.be.applied.no.sources.attached", refactoringName)
            }

            return showErrorHint(project, editor, message)
        }

        if (element.name == null) {
            val message = KotlinBundle.message("refactoring.cannot.be.applied.to.anonymous.function", refactoringName)
            return showErrorHint(project, editor, message)
        }

        val nameReference = editor?.findSimpleNameReference()
        val recursive = element.isRecursive()
        if (recursive && nameReference == null) {
            val message = RefactoringBundle.getCannotRefactorMessage(
                KotlinBundle.message("text.inline.recursive.function.is.supported.only.on.references")
            )

            return showErrorHint(project, editor, message)
        }

        val dialog = KotlinInlineFunctionDialog(
            element,
            nameReference,
            allowToInlineThisOnly = recursive,
            editor = editor,
        )

        if (!ApplicationManager.getApplication().isUnitTestMode) {
            dialog.show()
        } else {
            dialog.doAction()
        }
    }

    private fun KtNamedFunction.isRecursive(): Boolean {
        val context = analyzeWithContent()
        return bodyExpression?.includesCallOf(context[BindingContext.FUNCTION, this] ?: return false, context) ?: false
    }

    private fun KtExpression.includesCallOf(descriptor: FunctionDescriptor, context: BindingContext): Boolean {
        val refDescriptor = getResolvedCall(context)?.resultingDescriptor
        return descriptor == refDescriptor || anyDescendantOfType<KtExpression> {
            it !== this && descriptor == it.getResolvedCall(context)?.resultingDescriptor
        }
    }
}
