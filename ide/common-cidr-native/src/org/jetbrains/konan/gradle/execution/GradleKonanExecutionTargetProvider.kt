package org.jetbrains.konan.gradle.execution

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import javax.swing.Icon

class GradleKonanExecutionTargetProvider : ExecutionTargetProvider() {
    override fun getTargets(project: Project, configuration: RunConfiguration): List<ExecutionTarget> {
        val config = configuration as? GradleKonanAppRunConfiguration ?: return emptyList()
        return runReadAction { config.buildProfiles.map { GradleKonanBuildProfileExecutionTarget(it) } }
    }
}

class GradleKonanBuildProfileExecutionTarget(val profileName: String) : ExecutionTarget() {
    override fun getId(): String = "GradleKonanBuildProfile:$profileName"
    override fun getDisplayName(): String = profileName
    override fun getIcon(): Icon? = null
    override fun canRun(configuration: RunConfiguration): Boolean = configuration is GradleKonanAppRunConfiguration

    companion object {
        @JvmStatic
        fun getProfileName(target: ExecutionTarget): String? = (target as? GradleKonanBuildProfileExecutionTarget)?.profileName
    }
}

