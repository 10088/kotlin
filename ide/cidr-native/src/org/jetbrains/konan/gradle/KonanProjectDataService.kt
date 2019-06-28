/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.konan.gradle

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.PlatformUtils
import org.jetbrains.konan.gradle.execution.GradleKonanAppRunConfiguration
import org.jetbrains.konan.gradle.execution.GradleKonanAppRunConfigurationType
import org.jetbrains.konan.gradle.execution.GradleKonanTargetRunConfigurationProducer
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * @author Vladislav.Soroka
 */
class KonanProjectDataService : AbstractProjectDataService<KonanModel, Module>() {

    override fun getTargetDataKey(): Key<KonanModel> = KonanProjectResolver.KONAN_MODEL_KEY

    override fun postProcess(
        toImport: Collection<DataNode<KonanModel>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ) {
        GradleKonanWorkspace.getInstance(project).update()
    }

    override fun onSuccessImport(
        imported: Collection<DataNode<KonanModel>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModelsProvider
    ) {
        if (projectData?.owner != GradleConstants.SYSTEM_ID) return
        if (PlatformUtils.isCLion()) {
            createRunConfigurations(project)
        }
    }

    private fun createRunConfigurations(project: Project) {
        val workspace = GradleKonanWorkspace.getInstance(project)
        if (!workspace.isInitialized) return

        val runManager = RunManager.getInstance(project)
        var runConfigurationToSelect: RunnerAndConfigurationSettings? = null

        val configurationProducer = GradleKonanTargetRunConfigurationProducer.getGradleKonanInstance(project)!!
        val gradleAppRunConfigurationType = GradleKonanAppRunConfigurationType.instance

        workspace.buildTargets.map {
            // avoid adding run configurations for test executables unless there is no matching non-test (base) target
            // this is necessary to avoid polluting run configurations drop-down with too many choices
            it.baseBuildTarget ?: it
        }.forEach { buildTarget ->
            val templateConfiguration =
                    gradleAppRunConfigurationType.factory.createTemplateConfiguration(project) as GradleKonanAppRunConfiguration
            configurationProducer.setupTarget(templateConfiguration, listOf(buildTarget))

            val suggestedName = templateConfiguration.suggestedName() ?: return@forEach

            if (runManager.findConfigurationByTypeAndName(gradleAppRunConfigurationType, suggestedName) != null) {
                return@forEach
            }

            val runConfiguration = runManager.createConfiguration(suggestedName, gradleAppRunConfigurationType.factory)
            val configuration = runConfiguration.configuration as GradleKonanAppRunConfiguration
            configuration.name = suggestedName
            configurationProducer.setupTarget(configuration, listOf(buildTarget))

            runManager.addConfiguration(runConfiguration)
            if (runConfigurationToSelect == null) {
                runConfigurationToSelect = runConfiguration
            }
        }

        if (runConfigurationToSelect != null && runManager.selectedConfiguration == null) {
            val finalRunConfigurationToSelect = runConfigurationToSelect
            ApplicationManager.getApplication().invokeLater { runManager.selectedConfiguration = finalRunConfigurationToSelect }
        }
    }

    companion object {
        @JvmStatic
        fun forEachKonanProject(
            project: Project,
            consumer: (konanModel: KonanModel, moduleNode: DataNode<ModuleData>, rootProjectPath: String) -> Unit
        ) {
            for (projectInfo in ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID)) {
                val projectStructure = projectInfo.externalProjectStructure ?: continue
                val projectData = projectStructure.data
                val rootProjectPath = projectData.linkedExternalProjectPath
                val modulesNodes = ExternalSystemApiUtil.findAll(projectStructure, ProjectKeys.MODULE)
                for (moduleNode in modulesNodes) {
                    val projectNode = ExternalSystemApiUtil.find(moduleNode, KonanProjectResolver.KONAN_MODEL_KEY)
                    if (projectNode != null) {
                        val konanProject = projectNode.data
                        consumer(konanProject, moduleNode, rootProjectPath)
                    }
                }
            }
        }
    }

}
