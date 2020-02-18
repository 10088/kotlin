/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.konan.gradle.execution

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.CidrTargetRunConfigurationProducer

class GradleKonanTargetRunConfigurationProducer
    : CidrTargetRunConfigurationProducer<GradleKonanConfiguration, GradleKonanBuildTarget, GradleKonanAppRunConfiguration>(
        GradleKonanTargetRunConfigurationBinder
) {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return GradleKonanAppRunConfigurationType.instance.factory
    }

    companion object {

        private var INSTANCE: GradleKonanTargetRunConfigurationProducer? = null

        @Synchronized
        fun getGradleKonanInstance(project: Project): GradleKonanTargetRunConfigurationProducer? {
            if (INSTANCE != null) {
                return INSTANCE
            }
            for (cp in RunConfigurationProducer.getProducers(project)) {
                if (cp is GradleKonanTargetRunConfigurationProducer) {
                    INSTANCE = cp
                    return INSTANCE
                }
            }
            return null
        }
    }
}
