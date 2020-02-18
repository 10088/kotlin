/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.mobile.execution.testing

import com.intellij.execution.Executor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.jetbrains.cidr.execution.CidrConsoleBuilder
import com.jetbrains.cidr.execution.testing.*
import com.jetbrains.cidr.execution.testing.xctest.OCRerunFailedTestsAction
import com.jetbrains.cidr.execution.testing.xctest.OCUnitTestScopeElement
import java.io.File

class AppleXCTestCommandLineState(
    configuration: MobileTestRunConfiguration,
    launcher: CidrLauncher,
    env: ExecutionEnvironment,
    executor: Executor,
    failedTests: CidrTestScope?
) : CidrTestCommandLineState<MobileTestRunConfiguration>(configuration, launcher, env, executor, failedTests, EMPTY_TEST_SCOPE_PRODUCER) {
    init {
        consoleBuilder = object : CidrConsoleBuilder(configuration.project, null, configuration.project.basePath?.let { File(it) }) {
            override fun createConsole() = this@AppleXCTestCommandLineState.createConsole(this)
        }
    }

    override fun createTestScopeElement(testClass: String?, testMethod: String?): CidrTestScopeElement =
        OCUnitTestScopeElement(testClass, testMethod)

    override fun doCreateRerunFailedTestsAction(consoleView: SMTRunnerConsoleView): CidrRerunFailedTestsAction =
        OCRerunFailedTestsAction(consoleView, this)

    companion object {
        private val EMPTY_TEST_SCOPE_PRODUCER = {
            CidrTestScope.createEmptyTestScope(AppleXCTestFramework.instance.patternSeparatorInCommandLine)
        }
    }
}