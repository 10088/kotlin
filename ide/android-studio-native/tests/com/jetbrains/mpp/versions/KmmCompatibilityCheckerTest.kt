/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package com.jetbrains.mpp.versions

import com.jetbrains.mpp.versions.KmmCompatibilityChecker.CompatibilityCheckResult
import com.jetbrains.mpp.versions.KmmCompatibilityChecker.CompatibilityCheckResult.*
import org.jetbrains.kotlin.idea.KotlinPluginVersion
import org.junit.Test
import org.junit.Assert.*

class KmmCompatibilityCheckerUnitTests {

    @Test
    fun `compatibilty range is exactly two versions of Kotlin forward`() {
        check(
            actualKotlinVersion = "1.3.60-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KOTLIN
        )

        check(
            actualKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = COMPATIBLE
        )

        check(
            actualKotlinVersion = "1.3.80-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = COMPATIBLE
        )

        check(
            actualKotlinVersion = "1.3.90-dev-1333-Studio3.5.1-2",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KMM_PLUGIN
        )
    }

    @Test
    fun `dev, eap or release of actual Kotlin doesnt matter`() {
        check(
            actualKotlinVersion = "1.3.80-dev-543-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = COMPATIBLE
        )

        check(
            actualKotlinVersion = "1.3.90-eap-322-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KMM_PLUGIN
        )

        check(
            actualKotlinVersion = "1.3.60-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-dev-Studio3.5.1-1",
            expectedResult = OUTDATED_KOTLIN
        )

        check(
            actualKotlinVersion = "1.3.90-dev-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-dev-Studio3.5.1-1",
            expectedResult = OUTDATED_KMM_PLUGIN
        )
    }

    @Test
    fun `patch update of actual Kotlin doesnt matter`() {
        check(
            actualKotlinVersion = "1.3.62-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KOTLIN
        )

        check(
            actualKotlinVersion = "1.3.72-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = COMPATIBLE
        )

        check(
            actualKotlinVersion = "1.3.82-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = COMPATIBLE
        )

        check(
            actualKotlinVersion = "1.3.92-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KMM_PLUGIN
        )
    }

    @Test
    fun `patch version against which we compiled doesnt matter`() {
        check(
            actualKotlinVersion = "1.3.60-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.72-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KOTLIN
        )

        check(
            actualKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.72-release-Studio3.5.1-1",
            expectedResult = COMPATIBLE
        )

        check(
            actualKotlinVersion = "1.3.73-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.72-release-Studio3.5.1-1",
            expectedResult = COMPATIBLE
        )

        check(
            actualKotlinVersion = "1.3.80-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.72-release-Studio3.5.1-1",
            expectedResult = COMPATIBLE
        )

        check(
            actualKotlinVersion = "1.3.90-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.72-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KMM_PLUGIN
        )
    }

    @Test
    fun `minor version should be the same`() {
        check(
            actualKotlinVersion = "1.4.70-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KMM_PLUGIN
        )

        check(
            actualKotlinVersion = "1.2.70-release-Studio3.5.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KOTLIN
        )
    }

    @Test
    fun `ide version doesnt matter`() {
        check(
            actualKotlinVersion = "1.3.60-release-Studio4.0.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KOTLIN
        )

        check(
            actualKotlinVersion = "1.3.70-release-Studio4.0.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = COMPATIBLE
        )

        check(
            actualKotlinVersion = "1.3.80-release-Studio4.0.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = COMPATIBLE
        )

        check(
            actualKotlinVersion = "1.3.90-release-Studio4.0.1-1",
            compiledAgainstKotlinVersion = "1.3.70-release-Studio3.5.1-1",
            expectedResult = OUTDATED_KMM_PLUGIN
        )
    }


    private fun check(actualKotlinVersion: String, compiledAgainstKotlinVersion: String, expectedResult: CompatibilityCheckResult) {
        val actualResult = KmmCompatibilityChecker.checkVersions(
            actualKotlinVersion.toKotlinPluginVersion(),
            compiledAgainstKotlinVersion.toKotlinPluginVersion()
        )
        assertEquals(expectedResult, actualResult)
    }

    companion object {
        private fun String.toKotlinPluginVersion(): KotlinPluginVersion = KotlinPluginVersion.parse(this)!!
    }
}