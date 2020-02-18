package org.jetbrains.konan.resolve.translation

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ContentRootData
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiManager
import com.jetbrains.cidr.lang.symbols.OCSymbol
import com.jetbrains.cidr.lang.symbols.cpp.OCIncludeSymbol
import gnu.trove.THashSet
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.konan.resolve.konan.KonanBridgeVirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.util.GradleConstants

class KtFrameworkTranslator(val project: Project) {
    fun translateModule(konanFile: KonanBridgeVirtualFile): Sequence<OCSymbol> {
        val sources = collectSources(konanFile)
        val ktFile = sources.firstOrNull()?.let { PsiManager.getInstance(project).findFile(it) } as? KtFile
                     ?: return emptySequence()

        val baseDeclarations = KtFileTranslator(project).translateBase(ktFile)
        val includes = sources.asSequence().map { include(konanFile, it) }

        return baseDeclarations + includes
    }

    private fun collectSources(konanFile: KonanBridgeVirtualFile): List<VirtualFile> {
        val projectNode = ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID).first().externalProjectStructure as DataNode<*>
        val moduleNode = ExternalSystemApiUtil.find(projectNode, ProjectKeys.MODULE) {
            it.data.id == konanFile.target.name
        } ?: return emptyList()

        val vfs = LocalFileSystem.getInstance()

        return ExternalSystemApiUtil.findAll(moduleNode, GradleSourceSetData.KEY).asSequence()
            .flatMap { sourceSetData -> ExternalSystemApiUtil.findAll(sourceSetData, ProjectKeys.CONTENT_ROOT).asSequence() }
            .flatMap { node -> node.data.sourceRoots.asSequence() }
            .mapNotNull {path -> vfs.findFileByPath(path) }
            .flatMap { dir -> findAllSources(dir).asSequence() }
            .toList()
    }

    private fun findAllSources(sourceRoot: VirtualFile): List<VirtualFile> {
        val result = arrayListOf<VirtualFile>()

        VfsUtilCore.visitChildrenRecursively(sourceRoot, object : VirtualFileVisitor<Nothing>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (file.fileType == KotlinFileType.INSTANCE) {
                    result.add(file)
                }
                return true
            }
        })

        return result
    }

    private fun include(konanFile: KonanBridgeVirtualFile, target: VirtualFile): OCIncludeSymbol {
        return OCIncludeSymbol(konanFile, 0, target, OCIncludeSymbol.IncludePath.EMPTY, true, false, 0, null, true)
    }

    //todo[medvedev] copied from ContentRootData#getSourceRoots
    private val ContentRootData.sourceRoots: Set<String>
        get() {
            val sourceRoots = THashSet(FileUtil.PATH_HASHING_STRATEGY)
            for (externalSrcType in ExternalSystemSourceType.values()) {
                if (externalSrcType.javaSourceRootType != null) {
                    for (path in getPaths(externalSrcType)) {
                        if (path != null) {
                            sourceRoots += path.path
                        }
                    }
                }
            }
            return sourceRoots
        }

    //todo[medvedev] copied from ContentRootData#getJavaSourceRootType
    private val ExternalSystemSourceType.javaSourceRootType: JpsModuleSourceRootType<*>?
        get() {
            return when (this) {
                ExternalSystemSourceType.SOURCE -> JavaSourceRootType.SOURCE
                ExternalSystemSourceType.TEST -> JavaSourceRootType.TEST_SOURCE
                ExternalSystemSourceType.EXCLUDED -> null
                ExternalSystemSourceType.SOURCE_GENERATED -> JavaSourceRootType.SOURCE
                ExternalSystemSourceType.TEST_GENERATED -> JavaSourceRootType.TEST_SOURCE
                ExternalSystemSourceType.RESOURCE -> JavaResourceRootType.RESOURCE
                ExternalSystemSourceType.TEST_RESOURCE -> JavaResourceRootType.TEST_RESOURCE
                else -> null
            }
        }
}
