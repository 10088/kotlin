/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend.jvm

import org.jetbrains.kotlin.backend.common.serialization.mangle.AbstractKotlinMangler
import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinExportChecker
import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinMangleComputer
import org.jetbrains.kotlin.backend.common.serialization.mangle.MangleMode
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.NoMutableState
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.signaturer.FirMangler
import org.jetbrains.kotlin.types.KotlinType

@NoMutableState
class FirJvmKotlinMangler(private val session: FirSession) : AbstractKotlinMangler<FirDeclaration>(), FirMangler {

    override val FirDeclaration.mangleString: String
        get() = getMangleComputer(MangleMode.FULL, { it }).computeMangle(this)

    override val FirDeclaration.signatureString: String
        get() = getMangleComputer(MangleMode.SIGNATURE, { it }).computeMangle(this)

    override val FirDeclaration.fqnString: String
        get() = getMangleComputer(MangleMode.FQNAME, { it }).computeMangle(this)

    override fun FirDeclaration.isExported(compatibleMode: Boolean): Boolean = true

    override fun getExportChecker(compatibleMode: Boolean): KotlinExportChecker<FirDeclaration> = error("ll")

    override fun getMangleComputer(mode: MangleMode, app: (KotlinType) -> KotlinType): KotlinMangleComputer<FirDeclaration> {
        return FirJvmMangleComputer(StringBuilder(256), mode, session)
    }
}
