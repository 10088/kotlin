/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.konan.execution

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.history.ImportedTestRunnableState
import com.intellij.execution.ui.RunContentDescriptor
import com.jetbrains.cidr.execution.CidrCommandLineState
import com.jetbrains.cidr.execution.CidrRunner

class MobileRunner : CidrRunner() {
    override fun getRunnerId(): String = "MobileRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        super.canRun(executorId, profile) &&
                profile is MobileRunConfiguration &&
                (executorId == DefaultRunExecutor.EXECUTOR_ID || executorId == DefaultDebugExecutor.EXECUTOR_ID)

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        if (state is ImportedTestRunnableState) {
            return super.doExecute(state, environment)
        }

        val device = environment.executionTarget as Device
        val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID
        if (device is AppleDevice && (isDebug || device is ApplePhysicalDevice)) {
            return startDebugSession(state as CidrCommandLineState, environment, !isDebug).runContentDescriptor
        }
        return super.doExecute(state, environment)
    }
}