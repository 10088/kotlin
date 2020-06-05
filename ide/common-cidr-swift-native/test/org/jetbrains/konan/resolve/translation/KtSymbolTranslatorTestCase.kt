package org.jetbrains.konan.resolve.translation

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.ResolveState
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.CommonProcessors
import com.jetbrains.cidr.lang.CLanguageKind
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContext
import com.jetbrains.cidr.lang.symbols.objc.OCMemberSymbol
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache.SymbolsProperties
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache.SymbolsProperties.SymbolsKind
import com.jetbrains.swift.codeinsight.resolve.processor.CollectingSymbolProcessor
import com.jetbrains.swift.psi.SwiftDeclarationKind
import com.jetbrains.swift.symbols.SwiftMemberSymbol
import com.jetbrains.swift.symbols.SwiftTypeSymbol
import org.jetbrains.konan.resolve.konan.KonanBridgeFileManager
import org.jetbrains.konan.resolve.konan.KonanBridgePsiFile
import org.jetbrains.konan.resolve.konan.KonanTarget
import org.jetbrains.konan.resolve.symbols.KtSymbol
import org.jetbrains.konan.resolve.symbols.objc.KtOCClassSymbol
import org.jetbrains.konan.resolve.symbols.objc.KtOCInterfaceSymbol
import org.jetbrains.konan.resolve.symbols.swift.KtSwiftClassSymbol
import org.jetbrains.konan.resolve.symbols.swift.KtSwiftTypeSymbol
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.JUnit3WithIdeaConfigurationRunner
import org.junit.Assume
import org.junit.runner.RunWith
import java.util.*

@RunWith(JUnit3WithIdeaConfigurationRunner::class)
abstract class KtSymbolTranslatorTestCase : KotlinLightCodeInsightFixtureTestCase() {
    protected val cache: FileSymbolTablesCache
        get() = FileSymbolTablesCache.getInstance(project)

    protected object TestTarget : KonanTarget {
        override val moduleId: String
            get() = ":module"
        override val productModuleName: String
            get() = "MyModule"

        override fun equals(other: Any?): Boolean = super.equals(other)
        override fun hashCode(): Int = super.hashCode()
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE

    override fun setUp(): Unit = super.setUp().also {
        FileSymbolTablesCache.setShouldBuildTablesInTests(SymbolsProperties(SymbolsKind.ONLY_USED, false, false))
        FileSymbolTablesCache.forceSymbolsLoadedInTests(true)
    }

    override fun tearDown() {
        try {
            FileSymbolTablesCache.forceSymbolsLoadedInTests(null)
            FileSymbolTablesCache.setShouldBuildTablesInTests(null)
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    protected fun configure(code: String, fileName: String = "toTranslate"): KtFile =
        myFixture.configureByText("$fileName.kt", code) as KtFile

    protected fun contextForFile(virtualFile: VirtualFile): OCInclusionContext {
        Assume.assumeFalse("cache already contains file", cache.cachedFiles.contains(virtualFile))
        val bridgingFile = KonanBridgeFileManager.getInstance(project).forTarget(TestTarget, "testTarget.h")
        val psiBridgingFile = PsiManager.getInstance(project).findFile(bridgingFile) as KonanBridgePsiFile
        return OCInclusionContext.empty(CLanguageKind.OBJ_C, psiBridgingFile).apply {
            define(KonanTarget.PRODUCT_MODULE_NAME_KEY, TestTarget.productModuleName)
        }
    }

    protected fun assertOCInterfaceSymbol(translatedSymbol: KtOCInterfaceSymbol, expectedSuperType: String, expectLoaded: Boolean) {
        assertFalse("unexpected template symbol", translatedSymbol.isTemplateSymbol)
        assertEquals("unexpected super type", expectedSuperType, translatedSymbol.superType.name)
        assertEquals("unexpected loaded state", expectLoaded, translatedSymbol.stateLoaded)
    }

    protected fun assertSwiftInterfaceSymbol(
        translatedSymbol: KtSwiftClassSymbol,
        expectedSuperType: String,
        expectLoaded: Boolean,
        expectedContainingType: SwiftTypeSymbol?,
        vararg expectedContainedTypes: SwiftTypeSymbol
    ) {
        assertEquals("unexpected super type", expectedSuperType, translatedSymbol.superTypes.singleOrNull()?.referenceName)
        assertEquals("unexpected containing type", expectedContainingType, translatedSymbol.containingTypeSymbol)
        assertSameElements(
            "unexpected contained types",
            translatedSymbol.members.filterIsInstance<SwiftTypeSymbol>(), expectedContainedTypes.asList()
        )
        assertEquals("unexpected loaded state", expectLoaded, translatedSymbol.stateLoaded)
    }

    protected fun <T : KtSymbol> KtFileTranslator<T, *>.translate(file: KtFile): List<T> =
        mutableListOf<T>().also { translate(file, TestTarget.productModuleName, it) }

    protected val KtOCClassSymbol<*, *>.members: Collection<OCMemberSymbol>
        get() = CommonProcessors.CollectProcessor<OCMemberSymbol>().also {
            processMembers(null, null, it)
        }.results

    protected val KtSwiftTypeSymbol<*, *>.members: Collection<SwiftMemberSymbol>
        get() = CollectingSymbolProcessor<SwiftMemberSymbol>(null, null, EnumSet.allOf(SwiftDeclarationKind::class.java)).also {
            processMembers(it, ResolveState())
        }.collectedSymbols
}