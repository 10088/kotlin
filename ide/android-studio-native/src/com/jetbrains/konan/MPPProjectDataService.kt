/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.konan

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.configuration.KotlinTargetData
import org.jetbrains.kotlin.idea.configuration.readGradleProperty

class MPPProjectDataService : AbstractProjectDataService<KotlinTargetData, Void>() {
    override fun getTargetDataKey() = KotlinTargetData.KEY

    override fun onSuccessImport(
        imported: MutableCollection<DataNode<KotlinTargetData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModelsProvider
    ) {
        val workspace = MPPWorkspace.getInstance(project)
        workspace.xcproject = readGradleProperty(project, "xcodeproj")
    }
}