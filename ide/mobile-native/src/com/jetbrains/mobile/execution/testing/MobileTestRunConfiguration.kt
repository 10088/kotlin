package com.jetbrains.mobile.execution.testing

import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.CidrLauncher
import com.jetbrains.cidr.execution.testing.*
import com.jetbrains.mobile.execution.*
import com.jetbrains.mobile.isAndroid
import com.jetbrains.mobile.isApple
import com.jetbrains.mobile.isMobileAppTest
import org.jdom.Element
import java.io.File

class MobileTestRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    MobileRunConfigurationBase(project, factory, name),
    CidrTestRunConfiguration {

    fun getTestRunnerBundle(environment: ExecutionEnvironment): File {
        val app = getProductBundle(environment)
        val appName = app.nameWithoutExtension
        return when (environment.executionTarget) {
            is AppleDevice -> File(File(app, "PlugIns"), "${appName}Tests.xctest")
            is AndroidDevice -> File(File(File(app.parentFile.parentFile, "androidTest"), app.parentFile.name), "$appName-androidTest.apk")
            else -> throw IllegalStateException()
        }
    }

    private lateinit var testData: CidrTestRunConfigurationData<MobileTestRunConfiguration>

    fun recreateTestData() {
        val module = module ?: return
        testData = when {
            module.isAndroid -> AndroidTestRunConfigurationData(this)
            module.isApple -> AppleXCTestRunConfigurationData(this)
            else -> throw IllegalStateException()
        }
    }

    init {
        recreateTestData()
    }

    override fun getTestData(): CidrTestRunConfigurationData<MobileTestRunConfiguration> = testData

    override fun isSuitable(module: Module): Boolean = module.isMobileAppTest

    override fun createAppleState(environment: ExecutionEnvironment, executor: Executor, device: AppleDevice): CommandLineState =
        getTestData().createState(environment, executor, null)

    override fun createOtherState(environment: ExecutionEnvironment): CommandLineState = AndroidTestCommandLineState(this, environment)

    override fun createCidrLauncher(environment: ExecutionEnvironment, device: AppleDevice): CidrLauncher =
        AppleXCTestLauncher(this, environment, device)

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        MobileTestRunConfigurationEditor(project, helper, ::isSuitable)

    override fun createTestRunProfile(
        rerunAction: CidrRerunFailedTestsAction,
        testScope: CidrTestScope
    ): CidrRerunFailedTestsAction.CidrReturnTestProfile = CidrRerunFailedTestsAction.CidrReturnTestProfile(rerunAction, this, testScope)

    override fun createLauncher(environment: ExecutionEnvironment): CidrLauncher = throw IllegalStateException()

    override fun writeExternal(element: Element) {
        super<MobileRunConfigurationBase>.writeExternal(element)
        testData.writeExternal(element)
    }

    override fun readExternal(element: Element) {
        super<MobileRunConfigurationBase>.readExternal(element)
        recreateTestData()
        testData.readExternal(element)
    }

    override fun checkConfiguration() {
        super<MobileRunConfigurationBase>.checkConfiguration()
        testData.checkData()
    }

    override fun clone(): MobileTestRunConfiguration {
        val result = super.clone() as MobileTestRunConfiguration
        result.testData = testData.cloneForConfiguration(result) as CidrTestRunConfigurationData<MobileTestRunConfiguration>
        return result
    }
}