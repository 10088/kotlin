/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.konan.findUsages

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch.SearchParameters
import com.intellij.util.Processor
import com.jetbrains.cidr.lang.symbols.OCSymbol
import org.jetbrains.konan.resolve.symbols.KtSymbolPsiWrapper
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.collectAllExpectAndActualDeclaration
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.psi.KtDeclaration

abstract class KotlinUsageSearcher<T : OCSymbol, E : KtDeclaration> : QueryExecutorBase<PsiReference, SearchParameters>(true) {
    final override fun processQuery(parameters: SearchParameters, consumer: Processor<in PsiReference>) {
        val target = parameters.getTarget() ?: return

        val isMPP = (target.descriptor as? MemberDescriptor)?.let { it.isExpect || it.isActual } ?: false
        val symbols = if (!isMPP) {
            target.toLightSymbols()
        } else {
            target.collectAllExpectAndActualDeclaration().filter { it.platform.isNative() }.flatMap {
                @Suppress("UNCHECKED_CAST")
                (it as E).toLightSymbols()
            }
        }

        var effectiveSearchScope: SearchScope? = null
        for (symbol in symbols) {
            val word = symbol.word ?: continue
            val psiWrapper = createWrapper(target, symbol)
            if (effectiveSearchScope == null) {
                //infer effectiveSearchScope only once. it's the same for all symbols
                val symbolParameters = parameters.duplicateWith(psiWrapper)
                effectiveSearchScope = symbolParameters.effectiveSearchScope
            }
            parameters.optimizer.searchWord(word, effectiveSearchScope, UsageSearchContext.IN_CODE, true, psiWrapper)
        }
    }

    protected abstract fun SearchParameters.getTarget(): E?
    protected abstract fun E.toLightSymbols(): List<T>
    protected abstract fun createWrapper(target: E, symbol: T): KtSymbolPsiWrapper
    protected abstract val T.word: String?
}

internal fun SearchParameters.getUnwrappedTarget(): PsiElement =
    elementToSearch.let {
        when (it) {
            is KtLightElement<*, *> -> it.kotlinOrigin
            is KtSymbolPsiWrapper -> it.psi
            else -> null
        } ?: it
    }

internal fun SearchParameters.duplicateWith(psi: PsiElement): SearchParameters =
    SearchParameters(psi, scopeDeterminedByUser, isIgnoreAccessScope, optimizer)