/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.konan.gradle.execution

import com.jetbrains.cidr.execution.CidrBuildConfiguration
import org.jetbrains.kotlin.konan.target.CompilerOutputKind

import java.io.File
import java.io.Serializable

/**
 * @author Vladislav.Soroka
 */
class GradleKonanConfiguration(val id: String,
                               name: String,
                               val profileName: String,
                               val productFile: File?,
                               val targetType: CompilerOutputKind?,
                               val compileTaskName: String?,
                               val projectPath: String,
                               val isTests: Boolean) : Serializable, CidrBuildConfiguration {
    private val myName: String = "$name [$profileName]"

    val isExecutable: Boolean
        get() = targetType == CompilerOutputKind.PROGRAM

    override fun getName(): String {
        return myName
    }
}
