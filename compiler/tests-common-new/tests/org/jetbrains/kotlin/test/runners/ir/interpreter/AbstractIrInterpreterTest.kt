/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.test.runners.ir.interpreter

import org.jetbrains.kotlin.config.AnalysisFlag
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlin.test.backend.BlackBoxCodegenSuppressor
import org.jetbrains.kotlin.test.backend.handlers.IrInterpreterBackendHandler
import org.jetbrains.kotlin.test.builders.*
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.model.BinaryKind
import org.jetbrains.kotlin.test.model.DependencyKind
import org.jetbrains.kotlin.test.model.FrontendKind
import org.jetbrains.kotlin.test.model.FrontendKinds
import org.jetbrains.kotlin.test.preprocessors.IrInterpreterImplicitKotlinImports
import org.jetbrains.kotlin.test.runners.AbstractKotlinCompilerWithTargetBackendTest
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.configuration.CommonEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.configuration.JvmEnvironmentConfigurator
import org.jetbrains.kotlin.test.services.sourceProviders.IrInterpreterHelpersSourceFilesProvider

open class AbstractIrInterpreterTest(
    private val frontendKind: FrontendKind<*>, targetBackend: TargetBackend
) : AbstractKotlinCompilerWithTargetBackendTest(targetBackend) {
    override fun TestConfigurationBuilder.configuration() {
        globalDefaults {
            frontend = frontendKind
            artifactKind = BinaryKind.NoArtifact
            dependencyKind = DependencyKind.Source
        }

        useConfigurators(
            ::CommonEnvironmentConfigurator,
            ::IrInterpreterEnvironmentConfigurator,
        )

        firFrontendStep()
        classicFrontendStep()

        fir2IrStep()
        psi2IrStep()
        irHandlersStep {
            useHandlers(::IrInterpreterBackendHandler)
        }

        jvmIrBackendStep()

        useAfterAnalysisCheckers(::BlackBoxCodegenSuppressor)

        enableMetaInfoHandler()
    }
}

open class AbstractJvmIrInterpreterTest(frontendKind: FrontendKind<*>) : AbstractIrInterpreterTest(frontendKind, TargetBackend.JVM_IR) {
    override fun configure(builder: TestConfigurationBuilder) {
        super.configure(builder)
        with(builder) {
            globalDefaults {
                targetPlatform = JvmPlatforms.defaultJvmPlatform
            }

            defaultDirectives {
                +JvmEnvironmentConfigurationDirectives.FULL_JDK
                +JvmEnvironmentConfigurationDirectives.NO_RUNTIME
                +LanguageSettingsDirectives.ALLOW_KOTLIN_PACKAGE
            }

            useConfigurators(::JvmEnvironmentConfigurator)
            useAdditionalSourceProviders(::IrInterpreterHelpersSourceFilesProvider)
            useSourcePreprocessor(::IrInterpreterImplicitKotlinImports)
        }
    }
}

open class AbstractJvmIrInterpreterAfterFir2IrTest : AbstractJvmIrInterpreterTest(FrontendKinds.FIR)
open class AbstractJvmIrInterpreterAfterPsi2IrTest : AbstractJvmIrInterpreterTest(FrontendKinds.ClassicFrontend)

class IrInterpreterEnvironmentConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override fun provideAdditionalAnalysisFlags(
        directives: RegisteredDirectives,
        languageVersion: LanguageVersion
    ): Map<AnalysisFlag<*>, Any?> {
        return mapOf(AnalysisFlags.builtInsFromSources to true)
    }
}
