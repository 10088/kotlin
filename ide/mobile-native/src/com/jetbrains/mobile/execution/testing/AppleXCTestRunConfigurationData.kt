/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.mobile.execution.testing

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.cidr.execution.testing.CidrTestScope
import com.jetbrains.cidr.execution.testing.xctest.OCUnitRunConfigurationData
import com.jetbrains.cidr.execution.testing.xctest.OCUnitTestObject
import com.jetbrains.mobile.execution.AppleDevice

class AppleXCTestRunConfigurationData(configuration: MobileTestRunConfiguration) :
    OCUnitRunConfigurationData<MobileTestRunConfiguration>(configuration) {

    override fun getTestingFrameworkId(): String = "XCTest"

    override fun collectTestObjects(pathToFind: String): Collection<OCUnitTestObject> =
        AppleXCTestFramework.instance.collectTestObjects(pathToFind, myConfiguration.project, null)

    override fun createTestConsoleProperties(executor: Executor, executionTarget: ExecutionTarget): AppleXCTestConsoleProperties =
        AppleXCTestConsoleProperties(myConfiguration, executor, executionTarget)

    override fun createState(environment: ExecutionEnvironment, executor: Executor, failedTests: CidrTestScope?): CommandLineState =
        (environment.executionTarget as AppleDevice).createState(myConfiguration, environment, failedTests)

    override fun formatTestMethod(): String = "$testSuite.$testName"
}