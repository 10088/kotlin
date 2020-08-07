/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.inline

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.help.HelpManager
import com.intellij.refactoring.HelpID
import org.jetbrains.kotlin.idea.refactoring.KotlinRefactoringSettings
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinInlineFunctionDialog(
    function: KtNamedFunction,
    reference: KtSimpleNameReference?,
    editor: Editor?,
    private val allowInlineThisOnly: Boolean,
) : AbstractKotlinInlineDialog<KtNamedFunction>(function, reference, editor) {
    init {
        init()
    }

    override fun isInlineThis() = KotlinRefactoringSettings.instance.INLINE_METHOD_THIS

    public override fun doAction() {
        invokeRefactoring(
            KotlinInlineFunctionProcessor(
                declaration = declaration,
                reference = reference,
                inlineThisOnly = isInlineThisOnly || allowInlineThisOnly,
                deleteAfter = !isInlineThisOnly && !isKeepTheDeclaration && !allowInlineThisOnly,
                editor = editor
            )
        )

        val settings = KotlinRefactoringSettings.instance
        if (myRbInlineThisOnly.isEnabled && myRbInlineAll.isEnabled) {
            settings.INLINE_METHOD_THIS = isInlineThisOnly
        }
    }

    override fun doHelpAction() = HelpManager.getInstance().invokeHelp(
        if (declaration is KtConstructor<*>) HelpID.INLINE_CONSTRUCTOR else HelpID.INLINE_METHOD
    )

    override fun canInlineThisOnly() = allowInlineThisOnly
}
