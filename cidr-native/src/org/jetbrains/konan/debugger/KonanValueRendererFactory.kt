/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.konan.debugger

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriver
import com.jetbrains.cidr.execution.debugger.evaluation.ValueRendererFactory
import com.jetbrains.cidr.execution.debugger.evaluation.renderers.ValueRenderer

class KonanValueRendererFactory : ValueRendererFactory {
  override fun createRenderer(context: ValueRendererFactory.FactoryContext): ValueRenderer? {
    val process = context.physicalValue.process
    if (process.getUserData(prettyPrinters) == true) return null
    process.putUserData(prettyPrinters, true)

    process.postCommand { driver ->
      if (driver !is LLDBDriver) return@postCommand
      initLLDBDriver(process.project, driver)
    }
    return null
  }

  companion object {
    private val prettyPrinters = Key.create<Boolean>("KotlinPrettyPrinters")
  }
}


private fun initLLDBDriver(project: Project, driver: LLDBDriver) {
//  val lldbPrettyPrinters = KonanPaths.getInstance(project).konanDist()?.resolve("tools/konan_lldb.py")
//  if (lldbPrettyPrinters != null) {
//    driver.executeConsoleCommand("command script import \"$lldbPrettyPrinters\"")
//  }
  driver.executeConsoleCommand("settings set target.process.thread.step-avoid-regexp ^::Kotlin_")
}