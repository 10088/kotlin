/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.konan

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.ui.CommonProgramParametersPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfigurationEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.PanelWithAnchor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

class IdeaKonanRunConfigurationType : ConfigurationTypeBase(
    KonanBundle.message("id.runConfiguration"),
    KonanBundle.message("label.applicationName.text"),
    KonanBundle.message("label.applicationDescription.text"),
    AllIcons.RunConfigurations.Application
) {
    val factory: ConfigurationFactory
        get() = object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration {
                return IdeaKonanRunConfiguration(project, this, null)
            }

            override fun getId(): String = KonanBundle.message("id.factory")
        }

    init {
        addFactory(factory)
    }

    fun createEditor(project: Project): IdeaKonanRunConfigurationSettingsEditor {
        return IdeaKonanRunConfigurationSettingsEditor(project)
    }

    companion object {
        val instance: IdeaKonanRunConfigurationType
            get() = ConfigurationTypeUtil.findConfigurationType(IdeaKonanRunConfigurationType::class.java)
    }
}


class ComboBox : JComboBox<String>() {
    fun fireSelectedItemChanged() {
        println("@ComboBo:fireSelectedItemChanged")
    }
}


class IdeaKonanRunConfigurationSettingsEditor(val project: Project) :
    ExternalSystemRunConfigurationEditor(project, GradleConstants.SYSTEM_ID),
    PanelWithAnchor {

    private val availableExecutables = IdeaKonanWorkspace.getInstance(project).executables

    private var anchor: JComponent? = null
    private var commonProgramParameters: CommonProgramParametersPanel? = null

    private var executableLabel: JBLabel? = null
    private var executableCombo: JComboBox<String>? = null

    fun resetEditorFrom(runConfiguration: IdeaKonanRunConfiguration) {
        commonProgramParameters?.reset(runConfiguration)
        executableCombo?.selectedItem = runConfiguration.executable?.base?.name
    }

    override fun createEditor(): JComponent {
        val panel = JPanel(GridBagLayout())

        val gridBag = GridBag()
            .setDefaultFill(GridBagConstraints.BOTH)
            .setDefaultAnchor(GridBagConstraints.CENTER)
            .setDefaultWeightX(1, 1.0)
            .setDefaultInsets(0, JBUI.insets(0, 0, UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP))
            .setDefaultInsets(1, JBUI.insetsBottom(UIUtil.DEFAULT_VGAP))

        createEditorInner(panel, gridBag)
        setAnchor(commonProgramParameters?.anchor)
        return panel
    }

    private fun createEditorInner(panel: JPanel, gridBag: GridBag) {
        setupExecutableCombo(panel, gridBag)
        setupCommonProgramParametersPanel(panel, gridBag)
    }

    private fun setupCommonProgramParametersPanel(panel: JPanel, gridBag: GridBag) {
        commonProgramParameters = CommonProgramParametersPanel()
        panel.add(
            commonProgramParameters,
            gridBag.nextLine().weighty(1.0).insets(UIUtil.DEFAULT_VGAP * 2, -1, UIUtil.DEFAULT_VGAP * 2, -1).coverLine()
        )
    }

    private fun setupExecutableCombo(panel: JPanel, gridBag: GridBag) {
        executableLabel = JBLabel(KonanBundle.message("label.executable.text") + ":")
        executableCombo = ComboBox()

        availableExecutables.toSortedSet().forEach { executableCombo?.addItem(it.base.name) }

        panel.add(executableLabel, gridBag.nextLine().next())
        panel.add(executableCombo, gridBag.next().coverLine())
        executableLabel?.labelFor = executableCombo
    }

    fun applyEditorTo(runConfiguration: IdeaKonanRunConfiguration) {
        runConfiguration.apply {
            executable = availableExecutables.firstOrNull { it.base.name == executableCombo?.selectedItem } ?: executable
        }

        commonProgramParameters?.applyTo(runConfiguration)
    }

    override fun getAnchor(): JComponent? = anchor

    override fun setAnchor(anchor: JComponent?) {
        this.anchor = anchor
        executableLabel?.anchor = anchor
        commonProgramParameters?.anchor = anchor
    }
}