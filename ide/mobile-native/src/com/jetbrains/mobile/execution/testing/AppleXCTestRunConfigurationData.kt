package com.jetbrains.mobile.execution.testing

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.cidr.execution.testing.CidrTestScope
import com.jetbrains.cidr.execution.testing.xctest.OCUnitRunConfigurationData
import com.jetbrains.cidr.execution.testing.xctest.OCUnitTestObject

class AppleXCTestRunConfigurationData(configuration: MobileTestRunConfiguration) :
    OCUnitRunConfigurationData<MobileTestRunConfiguration>(configuration) {

    override fun getTestingFrameworkId(): String = "XCTest"

    override fun collectTestObjects(pathToFind: String): Collection<OCUnitTestObject> =
        AppleXCTestFramework.instance.collectTestObjects(pathToFind, myConfiguration.project, null)

    override fun createTestConsoleProperties(executor: Executor, executionTarget: ExecutionTarget): AppleXCTestConsoleProperties =
        AppleXCTestConsoleProperties(myConfiguration, executor, executionTarget)

    override fun createState(environment: ExecutionEnvironment, executor: Executor, failedTests: CidrTestScope?): CommandLineState =
        AppleXCTestCommandLineState(myConfiguration, environment, environment.executor, failedTests)

    override fun formatTestMethod(): String = "$testSuite.$testName"
}