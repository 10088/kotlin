package org.jetbrains.konan.resolve.konan

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration
import com.jetbrains.cidr.xcode.model.PBXTarget
import com.jetbrains.swift.codeinsight.resolve.AppcodeSourceModuleProducer
import com.jetbrains.swift.codeinsight.resolve.SwiftModule

class KonanSwiftSourceModuleProvider : AppcodeSourceModuleProducer {

    override fun create(parentConfiguration: OCResolveConfiguration,
                        target: PBXTarget,
                        configuration: OCResolveConfiguration?
    ): SwiftModule? {
        if (target.isKotlinTarget(parentConfiguration.project)) {
            return KonanSwiftSourceModule(configuration, target, parentConfiguration)
        }

        return null
    }

    private fun PBXTarget.isKotlinTarget(project: Project): Boolean =
        isFramework && sequenceOf(this).containsKotlinNativeTargets(project)
}