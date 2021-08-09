/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
package org.jetbrains.kotlin.backend.konan

import llvm.*
import org.jetbrains.kotlin.backend.common.serialization.KlibIrVersion
import org.jetbrains.kotlin.backend.common.serialization.metadata.KlibMetadataVersion
import org.jetbrains.kotlin.backend.konan.llvm.*
import org.jetbrains.kotlin.backend.konan.llvm.objc.linkObjC
import org.jetbrains.kotlin.konan.CURRENT
import org.jetbrains.kotlin.konan.CompilerVersion
import org.jetbrains.kotlin.konan.file.isBitcode
import org.jetbrains.kotlin.konan.library.impl.buildLibrary
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
import org.jetbrains.kotlin.library.*

/**
 * Supposed to be true for a single LLVM module within final binary.
 */
val CompilerOutputKind.isFinalBinary: Boolean get() = when (this) {
    CompilerOutputKind.PROGRAM, CompilerOutputKind.DYNAMIC,
    CompilerOutputKind.STATIC, CompilerOutputKind.FRAMEWORK -> true
    CompilerOutputKind.DYNAMIC_CACHE, CompilerOutputKind.STATIC_CACHE,
    CompilerOutputKind.LIBRARY, CompilerOutputKind.BITCODE -> false
}

val CompilerOutputKind.involvesBitcodeGeneration: Boolean
    get() = this != CompilerOutputKind.LIBRARY

internal val Context.producedLlvmModuleContainsStdlib: Boolean
    get() = this.llvmModuleSpecification.containsModule(this.stdlibModule)

val CompilerOutputKind.involvesLinkStage: Boolean
    get() = when (this) {
        CompilerOutputKind.PROGRAM, CompilerOutputKind.DYNAMIC,
        CompilerOutputKind.DYNAMIC_CACHE, CompilerOutputKind.STATIC_CACHE,
        CompilerOutputKind.STATIC, CompilerOutputKind.FRAMEWORK -> true
        CompilerOutputKind.LIBRARY, CompilerOutputKind.BITCODE -> false
    }

val CompilerOutputKind.isCache: Boolean
    get() = (this == CompilerOutputKind.STATIC_CACHE || this == CompilerOutputKind.DYNAMIC_CACHE)

internal fun produceCStubs(context: Context) {
    val llvmModule = context.llvmModule!!
    context.cStubsManager.compile(
            context.config.clang,
            context.messageCollector,
            context.inVerbosePhase
    ).forEach {
        parseAndLinkBitcodeFile(context, llvmModule, it.absolutePath)
    }
}

private fun linkAllDependencies(context: Context, generatedBitcodeFiles: List<String>) {
    val config = context.config

    val llvmModule = context.llvmModule!!

    programBitcode().forEach { fileBitcode ->
        val failed = llvmLinkModules2(context, llvmModule, fileBitcode)
        if (failed != 0) {
            throw Error("failed to link x") // TODO: retrieve error message from LLVM.
        }
    }

    val runtimeNativeLibraries = config.runtimeNativeLibraries
            .takeIf { context.producedLlvmModuleContainsStdlib }.orEmpty()

    val launcherNativeLibraries = config.launcherNativeLibraries
            .takeIf { config.produce == CompilerOutputKind.PROGRAM }.orEmpty()

    linkObjC(context, llvmModule)

    val nativeLibraries = config.nativeLibraries + runtimeNativeLibraries + launcherNativeLibraries

    val allLlvm = (programBitcode() + llvmModule)
            .asSequence()
            .map { moduleToLlvm.getValue(it).bitcodeToLink }
            .flatten()
            .map { it.bitcodePaths }
            .flatten()
            .filter { it.isBitcode }
            .toList()
    val bitcodeLibraries = allLlvm.toSet()
    val additionalBitcodeFilesToLink = context.llvm.additionalProducedBitcodeFiles
    val exceptionsSupportNativeLibrary = config.exceptionsSupportNativeLibrary
    val bitcodeFiles = (nativeLibraries + generatedBitcodeFiles + additionalBitcodeFilesToLink + bitcodeLibraries).toMutableSet()
    if (config.produce == CompilerOutputKind.DYNAMIC_CACHE)
        bitcodeFiles += exceptionsSupportNativeLibrary
    bitcodeFiles.forEach {
        parseAndLinkBitcodeFile(context, llvmModule, it)
    }
}

private fun computeClangInput(context: Context, generatedBitcodeFiles: List<String>) {
    val config = context.config

    val llvmModule = context.llvmModule!!


    val (llvmModules, objectFiles) = context.separateCompilation.classifyModules(programBitcode() + llvmModule)
    context.linkerInput += objectFiles
    context.optimizerInput += llvmModules

    val runtimeNativeLibraries = config.runtimeNativeLibraries
            .takeIf { context.producedLlvmModuleContainsStdlib }.orEmpty()

    val launcherNativeLibraries = config.launcherNativeLibraries
            .takeIf { config.produce == CompilerOutputKind.PROGRAM }.orEmpty()

    // link bitcode to make entry point pass work.
    launcherNativeLibraries.forEach {
        parseAndLinkBitcodeFile(context, llvmModule, it)
    }

    linkObjC(context, llvmModule)

    val nativeLibraries = config.nativeLibraries + runtimeNativeLibraries

    val allLlvm = programBitcode()
            .asSequence()
            .map { moduleToLlvm.getValue(it).bitcodeToLink }
            .flatten()
            .map { it.bitcodePaths }
            .flatten()
            .filter { it.isBitcode }
            .toList()
    val bitcodeLibraries = allLlvm.toSet()
    val additionalBitcodeFilesToLink = context.llvm.additionalProducedBitcodeFiles
    val exceptionsSupportNativeLibrary = config.exceptionsSupportNativeLibrary
    val bitcodeFiles = (nativeLibraries + generatedBitcodeFiles + additionalBitcodeFilesToLink + bitcodeLibraries).toMutableSet()
    if (config.produce == CompilerOutputKind.DYNAMIC_CACHE)
        bitcodeFiles += exceptionsSupportNativeLibrary
    val (bitcodes, objects) = context.separateCompilation.classifyBitcode(bitcodeFiles.toList())
    context.linkerInput += objects
    context.bitcodeFiles += bitcodes
}

private fun insertAliasToEntryPoint(context: Context) {
    val nomain = context.config.configuration.get(KonanConfigKeys.NOMAIN) ?: false
    if (context.config.produce != CompilerOutputKind.PROGRAM || nomain)
        return

    val module = context.llvmModule!!
    LLVMGetNamedFunction(module, "Konan_main")?.let { entryPoint ->
        LLVMAddAlias(module, LLVMTypeOf(entryPoint)!!, entryPoint, "main")
    }
}

internal fun linkBitcodeDependencies(context: Context) {
    val config = context.config.configuration
    val tempFiles = context.config.tempFiles
    val produce = config.get(KonanConfigKeys.PRODUCE)

    val generatedBitcodeFiles =
            if (produce == CompilerOutputKind.DYNAMIC || produce == CompilerOutputKind.STATIC) {
                produceCAdapterBitcode(
                        context.config.clang,
                        tempFiles.cAdapterCppName,
                        tempFiles.cAdapterBitcodeName)
                listOf(tempFiles.cAdapterBitcodeName)
            } else emptyList()
    if (produce == CompilerOutputKind.FRAMEWORK && context.config.produceStaticFramework) {
        embedAppleLinkerOptionsToBitcode(context.llvm, context.config)
    }
//    linkAllDependencies(context, generatedBitcodeFiles)
    computeClangInput(context, generatedBitcodeFiles)
}

internal fun produceOutput(context: Context) {

    val config = context.config.configuration
    val produce = config.get(KonanConfigKeys.PRODUCE)

    when (produce) {
        CompilerOutputKind.STATIC,
        CompilerOutputKind.DYNAMIC,
        CompilerOutputKind.FRAMEWORK,
        CompilerOutputKind.DYNAMIC_CACHE,
        CompilerOutputKind.STATIC_CACHE,
        CompilerOutputKind.PROGRAM -> {
        }
        CompilerOutputKind.LIBRARY -> {
            val nopack = config.getBoolean(KonanConfigKeys.NOPACK)
            val output = context.config.outputFiles.klibOutputFileName(!nopack)
            val libraryName = context.config.moduleId
            val shortLibraryName = context.config.shortModuleName
            val neededLibraries = context.librariesWithDependencies
            val abiVersion = KotlinAbiVersion.CURRENT
            val compilerVersion = CompilerVersion.CURRENT.toString()
            val libraryVersion = config.get(KonanConfigKeys.LIBRARY_VERSION)
            val metadataVersion = KlibMetadataVersion.INSTANCE.toString()
            val irVersion = KlibIrVersion.INSTANCE.toString()
            val versions = KotlinLibraryVersioning(
                abiVersion = abiVersion,
                libraryVersion = libraryVersion,
                compilerVersion = compilerVersion,
                metadataVersion = metadataVersion,
                irVersion = irVersion
            )
            val target = context.config.target
            val manifestProperties = context.config.manifestProperties

            if (!nopack) {
                val suffix = context.config.outputFiles.produce.suffix(target)
                if (!output.endsWith(suffix)) {
                    error("please specify correct output: packed: ${!nopack}, $output$suffix")
                }
            }

            buildLibrary(
                    context.config.nativeLibraries,
                    context.config.includeBinaries,
                    neededLibraries,
                    context.serializedMetadata!!,
                    context.serializedIr,
                    versions,
                    target,
                    output,
                    libraryName,
                    nopack,
                    shortLibraryName,
                    manifestProperties,
                    context.dataFlowGraph)
        }
        CompilerOutputKind.BITCODE -> {
            val output = context.config.outputFile
            LLVMWriteBitcodeToFile(context.llvmModule!!, output)
        }
        null -> {}
    }
}

private fun parseAndLinkBitcodeFile(context: Context, llvmModule: LLVMModuleRef, path: String) {
    val parsedModule = parseBitcodeFile(path)
    val failed = llvmLinkModules2(context, llvmModule, parsedModule)
    if (failed != 0) {
        throw Error("failed to link $path") // TODO: retrieve error message from LLVM.
    }
}

private fun embedAppleLinkerOptionsToBitcode(llvm: Llvm, config: KonanConfig) {
    fun findEmbeddableOptions(options: List<String>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val iterator = options.iterator()
        loop@while (iterator.hasNext()) {
            val option = iterator.next()
            result += when {
                option.startsWith("-l") -> listOf(option)
                option == "-framework" && iterator.hasNext() -> listOf(option, iterator.next())
                else -> break@loop // Ignore the rest.
            }
        }
        return result
    }

    val optionsToEmbed = findEmbeddableOptions(config.platform.configurables.linkerKonanFlags) +
            llvm.allNativeDependencies.flatMap { findEmbeddableOptions(it.linkerOpts) }

    embedLlvmLinkOptions(llvm.llvmModule, optionsToEmbed)
}
