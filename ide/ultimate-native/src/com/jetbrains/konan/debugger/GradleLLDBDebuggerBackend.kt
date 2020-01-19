/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.konan.debugger

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.externalSystem.debugger.DebuggerBackendExtension
import com.intellij.openapi.externalSystem.rt.execution.ForkedDebuggerHelper
import com.intellij.openapi.project.Project
import com.jetbrains.konan.IdeaKonanRunConfiguration
import com.jetbrains.konan.IdeaKonanRunConfigurationType
import com.jetbrains.konan.IdeaKonanWorkspace
import com.jetbrains.konan.KonanExecutable

class GradleLLDBDebuggerBackend : DebuggerBackendExtension {
    override fun id() = "Gradle LLDB"

    private fun debuggerSetupArgs(serverArgs: List<String>): String {
        return buildString {
            for (arg in serverArgs) {
                append("'$arg', ")
            }
            append("'127.0.0.1:' + debugPort, '--', task.executable")
        }
    }

    private fun codeFor(stageName: String, vararg codeLines: String): List<String> {
        // pick tasks suitable for debugging with lldb
        return listOf(
            """
            |gradle.taskGraph.$stageName { Task task ->
            |  def whiteList = [
            |    'org.gradle.api.tasks.Exec',
            |    'org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest'
            |  ]
            |  for (def klass = task.class; klass != Object.class; klass = klass.superclass) {
            |    if (whiteList.contains(klass.canonicalName)) {""".trimMargin()
        ) + codeLines.map { "      " + it.trim() }.toList() + listOf(
            """
            |      break
            |    }
            |  }
            |}""".trimMargin()
        )
    }

    override fun initializationCode(dispatchPort: String, sertializedParams: String): List<String> {
        val params = splitParameters(sertializedParams)
        val debugServerPath = params[DEBUG_SERVER_PATH_KEY] ?: return emptyList()
        val debugServerArgs = params[DEBUG_SERVER_ARGS_KEY]?.split(":") ?: emptyList()

        return codeFor(
            "beforeTask",
            "def debugPort = ForkedDebuggerHelper.setupDebugger('${id()}', task.path, '', $dispatchPort)",
            "task.args = [" + debuggerSetupArgs(debugServerArgs) + "] + task.args",
            "task.executable = new File('$debugServerPath')"
        ) + codeFor(
            "afterTask",
            "ForkedDebuggerHelper.signalizeFinish('${id()}', task.path, $dispatchPort)"
        )
    }

    private fun findKonanConfiguration(runManager: RunManager, konanExecutable: KonanExecutable): IdeaKonanRunConfiguration {
        val result = runManager.allSettings.firstOrNull {
            (it.configuration as? IdeaKonanRunConfiguration)?.executable == konanExecutable
        } ?: throw ExecutionException("No configuration for executable=${konanExecutable.base}")

        return result.configuration as IdeaKonanRunConfiguration
    }

    private fun findExecutable(project: Project, processName: String): KonanExecutable? {
        val workspace = IdeaKonanWorkspace.getInstance(project)

        if (processName.startsWith("run")) {
            val executableId = processName.removePrefix("run")
            return workspace.executables.find {
                it.executionTargets.any { t -> t.gradleTask.contains(executableId) }
            }
        }

        if (processName.endsWith("Test")) {
            val targetId = processName.removeSuffix("Test")
            return workspace.executables.find {
                it.base.name.contains(targetId) && it.base.name.contains("test")
            }
        }

        return null
    }

    override fun debugConfigurationSettings(
        project: Project,
        processName: String,
        processParameters: String
    ): RunnerAndConfigurationSettings {
        val workspace = IdeaKonanWorkspace.getInstance(project)
        val runManager = RunManager.getInstance(project)

        val isTest = processName.endsWith("Test")

        val executable = findExecutable(project, processName.substring(processName.lastIndexOf(':') + 1))
            ?: throw ExecutionException("No executable for processName=$processName")

        val params = splitParameters(processParameters)

        val settings = runManager.createConfiguration(processName, IdeaKonanRunConfigurationType.instance.factory)
        with(settings.configuration as IdeaKonanRunConfiguration) {
            if (isTest) {
                programParameters = "$processParameters --ktest_no_exit_code"
            }
            copyFrom(findKonanConfiguration(runManager, executable))
            debugPort = params[ForkedDebuggerHelper.DEBUG_SERVER_PORT_KEY]?.toInt()
        }

        settings.isActivateToolWindowBeforeRun = false
        return settings
    }

    companion object {
        const val DEBUG_SERVER_PATH_KEY = "DEBUG_SERVER_PATH"
        const val DEBUG_SERVER_ARGS_KEY = "DEBUG_SERVER_ARGS"
    }
}