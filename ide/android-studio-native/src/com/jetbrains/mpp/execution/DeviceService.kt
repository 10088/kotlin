/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.mpp.execution

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.jetbrains.cidr.execution.deviceSupport.AMDeviceManager
import com.jetbrains.cidr.execution.simulatorSupport.SimulatorsRegistry

class DeviceService {
    fun getAll(): List<Device> = getAppleDevices() + getAppleSimulators()

    private fun getAppleDevices(): List<ApplePhysicalDevice> =
        AMDeviceManager.getInstance().devices
            .filter { it.deviceType.isIOS }
            .map(::ApplePhysicalDevice)

    private fun getAppleSimulators(): List<AppleSimulator> =
        SimulatorsRegistry.getInstance().configurations.map(::AppleSimulator)

    companion object {
        val instance: DeviceService get() = service()
        private val log = logger<DeviceService>()
    }
}