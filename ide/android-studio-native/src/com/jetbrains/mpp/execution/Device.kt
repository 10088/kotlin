/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.mpp.execution

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.mpp.AppleRunConfiguration
import javax.swing.Icon

abstract class Device(
    private val uniqueID: String,
    val name: String,
    val osName: String,
    val osVersion: String
) : ExecutionTarget() {
    override fun getId(): String = uniqueID
    override fun getDisplayName(): String = "$name | $osName $osVersion"
    override fun getIcon(): Icon? = null

    abstract fun createState(configuration: AppleRunConfiguration, environment: ExecutionEnvironment): CommandLineState
}