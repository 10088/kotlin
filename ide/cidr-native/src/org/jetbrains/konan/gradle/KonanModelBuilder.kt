/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.konan.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.jetbrains.konan.gradle.KotlinNativeHomeEvaluator.getKotlinNativeHome
import org.jetbrains.konan.gradle.KonanModel.Companion.NO_KOTLIN_NATIVE_HOME
import org.jetbrains.konan.gradle.KonanModel.Companion.NO_TASK_PATH
import org.jetbrains.kotlin.gradle.KotlinMPPGradleModelBuilder.Companion.getTargets
import org.jetbrains.kotlin.gradle.getMethodOrNull
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import java.io.File

class KonanModelBuilder : ModelBuilderService {
    override fun getErrorMessageBuilder(project: Project, e: Exception): ErrorMessageBuilder {
        return ErrorMessageBuilder
            .create(project, e, "Gradle import errors")
            .withDescription("Unable to build Konan project configuration")
    }

    override fun canBuild(modelName: String?) = modelName == KonanModel::class.java.name

    override fun buildAll(modelName: String, project: Project): Any? {
        val targets = project.getTargets() ?: return null

        val artifacts = collectArtifacts1320(targets).takeIf { it.isNotEmpty() } ?: collectArtifactsLegacy(targets)

        val buildModuleTaskPath = project.tasks.findByName("assemble")?.path ?: NO_TASK_PATH
        val cleanModuleTaskPath = project.tasks.findByName("clean")?.path ?: NO_TASK_PATH
        val kotlinNativeHome = getKotlinNativeHome(project) ?: NO_KOTLIN_NATIVE_HOME

        return KonanModelImpl(artifacts, buildModuleTaskPath, cleanModuleTaskPath, kotlinNativeHome)
    }

    // This is for Kotlin 1.3.20+:
    private fun collectArtifacts1320(targets: Collection<*>) = targets
        .flatMap { target -> target["getBinaries"] as? Collection<*> ?: emptyList<Any>() }
        .mapNotNull { binary ->
            val linkTask = binary["getLinkTask"] as? Task ?: return@mapNotNull null
            val runTask = binary["getRunTask"] as? Exec
            buildArtifact(linkTask, runTask)
        }

    // Legacy way (< 1.3.20):
    private fun collectArtifactsLegacy(targets: Collection<*>) = targets
        .flatMap { target -> target["getCompilations"] as NamedDomainObjectContainer<*> }
        .flatMap { compilation ->
            val outputKinds = compilation["getOutputKinds"] as? List<*>
            val buildTypes = compilation["getBuildTypes"] as? List<*>
            outputKinds.orEmpty().flatMap { outputKind ->
                buildTypes.orEmpty().mapNotNull { buildType ->
                    outputKind ?: return@mapNotNull null
                    buildType ?: return@mapNotNull null
                    compilation["getLinkTask", outputKind, buildType] as? Task
                }
            }
        }
        .mapNotNull { buildArtifact(it, null) }

    @Suppress("UNCHECKED_CAST")
    private fun buildArtifact(linkTask: Task, runTask: Exec?): KonanModelArtifact? {
        val outputKind = linkTask["getOutputKind"]["name"] as? String ?: return null
        val konanTargetName = linkTask["getTarget"] as? String ?: error("No arch target found")
        val outputFile = (linkTask["getOutputFile"] as? Provider<*>)?.orNull as? File ?: return null
        val compilationTarget = linkTask["getCompilation"]["getTarget"]
        val compilationTargetName = compilationTarget["getName"] as? String ?: return null
        val isTests = linkTask["getProcessTests"] as? Boolean ?: return null

        val execConfiguration = if (runTask != null) {
            val workingDir: String = runTask.workingDir.path
            val programParameters: List<String> = runTask.args as List<String>? ?: emptyList()
            val environmentVariables: Map<String, String> = (runTask.environment as Map<String, Any>?)
                    ?.mapValues { it.value.toString() } ?: emptyMap()

            KonanModelArtifactExecConfigurationImpl(
                    workingDir,
                    programParameters,
                    environmentVariables
            )
        } else null

        return KonanModelArtifactImpl(
            compilationTargetName,
            CompilerOutputKind.valueOf(outputKind),
            konanTargetName,
            outputFile,
            linkTask.path,
            execConfiguration,
            isTests
        )
    }

    private operator fun Any?.get(methodName: String, vararg params: Any): Any? {
        return this[methodName, params.map { it.javaClass }, params.toList()]
    }

    private operator fun Any?.get(methodName: String, paramTypes: List<Class<*>>, params: List<Any?>): Any? {
        if (this == null) return null
        return this::class.java.getMethodOrNull(methodName, *paramTypes.toTypedArray())?.invoke(this, *params.toTypedArray())
    }
}
