/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.mpp

import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.CidrPathManager
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration
import java.io.File

class AppleLLDBDriverConfiguration : LLDBDriverConfiguration() {

    override fun getLLDBFrameworkFile(architecture: ArchitectureType) = CidrPathManager.getBinFile(
        AppleLLDBDriverConfiguration::class.java,
        "",
        "LLDB.framework",
        null
    )

    override fun getLLDBFrontendFile(architecture: ArchitectureType): File {
        return CidrPathManager.getBinFile(
            AppleLLDBDriverConfiguration::class.java,
            "",
            "LLDBFrontend",
            null
        )
    }
}