/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.konan.gradle

/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.konan.*
import org.jetbrains.kotlin.gradle.KonanRunConfigurationModel
import org.jetbrains.kotlin.idea.configuration.KotlinTargetData
import org.jetbrains.kotlin.idea.configuration.kotlinNativeHome
import org.jetbrains.plugins.gradle.util.GradleConstants

class IdeaKonanProjectDataService : AbstractProjectDataService<KotlinTargetData, Void>() {
    override fun getTargetDataKey() = KotlinTargetData.KEY

    private val configurationFactory = IdeaKonanRunConfigurationType.instance.factory

    override fun postProcess(
        toImport: Collection<DataNode<KotlinTargetData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider
    ) {}

    private fun getKonanHome(nodes: Collection<DataNode<KotlinTargetData>>): String? {
        val moduleData = nodes.firstOrNull()?.parent as? DataNode<ModuleData> ?: return null
        return moduleData.kotlinNativeHome
    }

    private fun collectConfigurations(
        project: Project,
        targetNodes: Collection<DataNode<KotlinTargetData>>
    ): List<IdeaKonanRunConfiguration> {
        val result = ArrayList<IdeaKonanRunConfiguration>()

        val executionTargets = HashMap<KonanExecutableBase, ArrayList<IdeaKonanExecutionTarget>>()
        val runConfigurations = HashMap<KonanExecutableBase, KonanRunConfigurationModel>()

        targetNodes.forEach { node ->
            val targetName = node.data.externalName

            node.data.konanArtifacts?.forEach { artifact ->
                val executable = KonanExecutableBase.constructFrom(artifact, targetName) ?: return@forEach
                val runProfile = IdeaKonanExecutionTarget.constructFrom(artifact, executable.name) ?: return@forEach
                executionTargets.getOrPut(executable) { ArrayList() } += runProfile
                artifact.runConfiguration?.let { runConfigurations.put(executable, it) }
            }
        }

        executionTargets.forEach { (executableBase, runProfiles) ->
            val executable = KonanExecutable(executableBase, runProfiles)
            val configuration = IdeaKonanRunConfiguration(project, configurationFactory, executable).apply {
                selectedTarget = runProfiles.firstOrNull()
                runConfigurations[executableBase]?.let {
                    workingDirectory = it.workingDirectory
                    programParameters = ParametersListUtil.join(it.programParameters)
                    envs = filterOutSystemEnvs(it.environmentVariables)
                }
            }

            result.add(configuration)
        }

        return result
    }

    private fun updateProject(project: Project, runConfigurations: List<IdeaKonanRunConfiguration>, konanHome: String?) {
        val runManager = RunManager.getInstance(project)
        val workspace = IdeaKonanWorkspace.getInstance(project)

        workspace.executables.clear()
        konanHome?.let {
            workspace.konanHome = it
            workspace.konanVersion = getKotlinNativeVersion(it)
        }

        runConfigurations.sortedBy { it.name }.forEach { runConfiguration ->
            val executable = runConfiguration.executable ?: return@forEach
            workspace.executables.add(executable)
            val ideConfiguration = runManager.createConfiguration(executable.base.name, configurationFactory)
            (ideConfiguration.configuration as IdeaKonanRunConfiguration).copyFrom(runConfiguration)
            updateConfiguration(runManager, ideConfiguration)
        }

        runManager.apply {
            selectedConfiguration = selectedConfiguration ?: allSettings.firstOrNull()
        }
    }

    // preserves manually typed run configuration data when gradle provides nothing in return
    private fun updateConfiguration(runManager: RunManager, newSettings: RunnerAndConfigurationSettings) {
        runManager.allSettings.firstOrNull { it.name == newSettings.name }?.let { oldSettings ->
            val newConfiguration = newSettings.configuration as IdeaKonanRunConfiguration
            val oldConfiguration = oldSettings.configuration as? IdeaKonanRunConfiguration ?: return@let

            newConfiguration.workingDirectory = newConfiguration.workingDirectory ?: oldConfiguration.workingDirectory
            newConfiguration.programParameters = newConfiguration.programParameters ?: oldConfiguration.programParameters
            if (newConfiguration.envs.isEmpty())  newConfiguration.envs = oldConfiguration.envs
            runManager.removeConfiguration(oldSettings)
        }

        runManager.addConfiguration(newSettings)
    }

    override fun onSuccessImport(
        imported: Collection<DataNode<KotlinTargetData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModelsProvider
    ) {
        if (projectData?.owner != GradleConstants.SYSTEM_ID) return
        val configurations = collectConfigurations(project, imported)
        val konanHome = getKonanHome(imported)

        updateProject(project, configurations, konanHome)
    }
}