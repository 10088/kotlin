/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.mpp.execution

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.simulatorSupport.SimulatorsRegistry
import com.jetbrains.mpp.AppleRunConfiguration

class DeviceExecutionTargetProvider : ExecutionTargetProvider() {
    override fun getTargets(project: Project, configuration: RunConfiguration): List<ExecutionTarget> {
        if (configuration !is AppleRunConfiguration) {
            return emptyList()
        }

        return SimulatorsRegistry.getInstance().configurations.map(::AppleSimulator)
    }
}