/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.mpp.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.CidrCommandLineState
import com.jetbrains.konan.KonanCommandLineState
import com.jetbrains.konan.KonanExternalSystemState
import com.jetbrains.konan.RunnerBase
import com.jetbrains.mpp.AppleRunConfiguration
import com.jetbrains.mpp.ProjectWorkspace

class AppleRunner : RunnerBase() {

    override fun getRunnerId(): String = "AppleRunner"

    override fun getWorkspace(project: Project) = ProjectWorkspace.getInstance(project)

    override fun canRun(executorId: String, profile: RunProfile) = when (profile) {
        is BinaryRunConfiguration -> canRunBinary(executorId, profile)
        is AppleRunConfiguration -> true
        else -> false
    }

    @Throws(ExecutionException::class)
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        if (environment.executor.id != DefaultDebugExecutor.EXECUTOR_ID) {
            if (state is CidrCommandLineState && state.launcher is ApplePhysicalDeviceLauncher) {
                return contentDescriptor(environment) { session ->
                    (state.launcher as ApplePhysicalDeviceLauncher).withoutBreakpoints = true
                    state.startDebugProcess(session)
                }
            }

            return super.doExecute(state, environment)
        }

        return when (state) {
            is CidrCommandLineState -> contentDescriptor(environment) { session -> state.startDebugProcess(session) }
            is KonanCommandLineState -> contentDescriptor(environment) { session -> state.startDebugProcess(session) }
            is KonanExternalSystemState -> contentDescriptor(environment) { session -> state.startDebugProcess(session, environment) }
            else -> throw ExecutionException("RunProfileState  ${state.javaClass} is not supported by ${this.javaClass}")
        }
    }
}