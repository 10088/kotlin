/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.konan

import com.intellij.build.BuildViewManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.kotlin.konan.KonanVersion
import org.jetbrains.kotlin.konan.library.lite.LiteKonanDistributionProvider
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File

fun runBuildTasks(
    project: Project,
    executionName: String,
    taskNames: List<String>,
    projectPath: String,
    activateToolWindowBeforeRun: Boolean,
    env: Map<String, String>? = null
): Boolean {
    val settings = ExternalSystemTaskExecutionSettings().apply {
        this.executionName = executionName
        externalProjectPath = projectPath
        this.taskNames = taskNames
        externalSystemIdString = GradleConstants.SYSTEM_ID.id
        env?.let { this.env = it }
    }

    val userData = UserDataHolderBase()
    userData.putUserData(ExternalSystemRunConfiguration.PROGRESS_LISTENER_KEY, BuildViewManager::class.java)

    val result = Ref.create(false)
    val finished = Semaphore(1)
    val taskCallback = object : TaskCallback {
        override fun onSuccess() {
            result.set(true)
            finished.up()
        }

        override fun onFailure() {
            result.set(false)
            finished.up()
        }
    }

    ExternalSystemUtil.runTask(
        settings,
        DefaultRunExecutor.EXECUTOR_ID,
        project,
        GradleConstants.SYSTEM_ID,
        taskCallback,
        ProgressExecutionMode.IN_BACKGROUND_ASYNC,
        activateToolWindowBeforeRun,
        userData
    )
    finished.waitFor()
    return result.get()
}

// Returns Kotlin/Native internal version (not the same as Big Kotlin version).
fun getKotlinNativeVersion(kotlinNativeHome: String): KonanVersion? {
    return LiteKonanDistributionProvider.getDistribution(File(kotlinNativeHome))?.konanVersion
}

fun filterOutSystemEnvs(user: Map<String, String>): MutableMap<String, String> {
    val result = LinkedHashMap<String, String>()
    val parental = GeneralCommandLine().parentEnvironment

    for ((key, value) in user) {
        if (!parental.containsKey(key)) {
            result[key] = value
        }
    }

    return result
}
