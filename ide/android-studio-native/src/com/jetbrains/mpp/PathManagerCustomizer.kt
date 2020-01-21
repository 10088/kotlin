/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.mpp

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.cidr.OCPathManagerCustomization
import java.io.File

class PathManagerCustomization : OCPathManagerCustomization() {
    override fun getBinFile(relativePath: String): File =
        File(FileUtil.join(PathManager.getPluginsPath(), "mobile-mpp", "native", relativePath)).also {
            FileUtil.setExecutable(it) // FIXME omit when CLion build script is corrected
        }
}