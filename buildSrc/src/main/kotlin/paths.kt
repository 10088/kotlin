/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

@file:JvmName("UltimateBuildSrc")
@file:JvmMultifileClass

package org.jetbrains.kotlin.ultimate

import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.*

// absolute path to the "kotlin-ultimate" sub-project in multi-project Gradle build
private const val KOTLIN_ULTIMATE_SUBPROJECT_PATH_IN_KOTLIN = ":kotlin-ultimate"

fun Project.ultimatePath(path: String): String {
    check(path.startsWith(":")) { "Kotlin Ultimate Gradle path should start with :" }
    check(!path.startsWith(KOTLIN_ULTIMATE_SUBPROJECT_PATH_IN_KOTLIN)) {
        "Kotlin Ultimate Gradle path should not start with $KOTLIN_ULTIMATE_SUBPROJECT_PATH_IN_KOTLIN"
    }

    val ultimateProject = if (isStandaloneBuild)
        rootProject
    else
        rootProject.findProject(KOTLIN_ULTIMATE_SUBPROJECT_PATH_IN_KOTLIN)
                ?: error("Can't locate \"$KOTLIN_ULTIMATE_SUBPROJECT_PATH_IN_KOTLIN\" project")

    val ultimateProjectPath = ultimateProject.path
    return if (ultimateProjectPath == ":" && path.isNotEmpty())
        path
    else
        ultimateProjectPath + path
}

fun Project.ultimateProject(path: String): Project = project(ultimatePath(path))

fun Project.ultimateProjectDep(path: String, configuration: String? = null): ProjectDependency =
    dependencies.project(ultimatePath(path), configuration)
