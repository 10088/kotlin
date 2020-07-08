/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.kmm.ios

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.ui.IconManager

class AppleRunConfigurationType : ConfigurationTypeBase(
    ID,
    "iOS App",
    "Kotlin multiplatform mobile iOS application",
    ICON
) {
    init {
        addFactory(AppleConfigurationFactory(this))
    }

    companion object {
        internal const val ID = "KmmRunConfiguration"
        private val ICON = IconManager.getInstance().getIcon(
            "/META-INF/appleRunConfigurationIcon.svg",
            AppleRunConfigurationType::class.java
        )
    }
}

class AppleConfigurationFactory(type: AppleRunConfigurationType) : ConfigurationFactory(type) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return AppleRunConfiguration(project, this, name)
    }
}