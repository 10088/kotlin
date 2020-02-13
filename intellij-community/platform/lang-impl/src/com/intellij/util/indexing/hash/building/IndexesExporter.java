// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.hash.building;

import com.intellij.concurrency.JobLauncher;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.stubs.SerializationManagerImpl;
import com.intellij.psi.stubs.StubIndexExtension;
import com.intellij.psi.stubs.StubSharedIndexExtension;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.hash.ContentHashEnumerator;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContentImpl;
import com.intellij.util.indexing.IndexInfrastructureVersion;
import com.intellij.util.indexing.snapshot.IndexedHashesSupport;
import com.intellij.util.io.PathKt;
import com.intellij.util.io.zip.JBZipEntry;
import com.intellij.util.io.zip.JBZipFile;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

@SuppressWarnings("HardCodedStringLiteral")
public class IndexesExporter {
  private static final Logger LOG = Logger.getInstance(IndexesExporter.class);

  private final Project myProject;

  public IndexesExporter(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  public static IndexesExporter getInstance(@NotNull Project project) {
    return project.getService(IndexesExporter.class);
  }

  public IndexInfrastructureVersion exportIndexesChunk(@NotNull IndexChunk chunk, @NotNull Path zipFile, @NotNull ProgressIndicator indicator) {
    Path chunkRoot = getUnpackedIndexRoot(zipFile);
    IndexInfrastructureVersion version = ReadAction.compute(() -> processChunkUnderReadAction(chunkRoot, chunk, indicator));
    zipIndexOut(chunkRoot, zipFile, indicator);
    return version;
  }

  @NotNull
  private static Path getUnpackedIndexRoot(@NotNull Path indexZipFile) {
    Path indexRoot = indexZipFile.resolveSibling(indexZipFile.getFileName().toString() + ".unpacked");
    PathKt.delete(indexRoot);
    return PathKt.createDirectories(indexRoot);
  }

  @NotNull
  private IndexInfrastructureVersion processChunkUnderReadAction(@NotNull Path chunkRoot,
                                                                 @NotNull IndexChunk chunk,
                                                                 @NotNull ProgressIndicator indicator) {
    List<FileBasedIndexExtension<?, ?>> exportableFileBasedIndexExtensions = FileBasedIndexExtension
      .EXTENSION_POINT_NAME
      .extensions()
      .filter(FileBasedIndexExtension::dependsOnFileContent)
      .filter(ex -> !(ex instanceof StubUpdatingIndex))
      .collect(Collectors.toList());
    List<HashBasedIndexGenerator<?, ?>> fileBasedGenerators = ContainerUtil.map(exportableFileBasedIndexExtensions, ex -> getGenerator(chunkRoot, ex));

    List<StubIndexExtension<?, ?>> exportableStubIndexExtensions = StubIndexExtension.EP_NAME.getExtensionList();

    Path stubIndexesRoot = chunkRoot.resolve(StubUpdatingIndex.INDEX_ID.getName());
    Path serializerNamesStorageFile = StubSharedIndexExtension.getStubSerializerNamesStorageFile(stubIndexesRoot);
    SerializationManagerImpl chunkSerializationManager = new SerializationManagerImpl(serializerNamesStorageFile, false);
    StubHashBasedIndexGenerator stubGenerator = StubHashBasedIndexGenerator.create(stubIndexesRoot, chunkSerializationManager, exportableStubIndexExtensions);

    try {
      List<HashBasedIndexGenerator<?, ?>> allGenerators = new ArrayList<>(fileBasedGenerators);
      allGenerators.add(stubGenerator);

      generate(chunk, allGenerators, chunkRoot, indicator);
    } finally {
      Disposer.dispose(chunkSerializationManager);
    }

    deleteEmptyIndices(fileBasedGenerators, chunkRoot, false);
    deleteEmptyIndices(stubGenerator.getStubGenerators(), chunkRoot, true);

    IndexInfrastructureVersion indexInfrastructureVersion = IndexInfrastructureVersion.fromExtensions(exportableFileBasedIndexExtensions,
                                                                                                      exportableStubIndexExtensions);
    printStatistics(chunk, fileBasedGenerators, stubGenerator);
    return indexInfrastructureVersion;
  }

  @NotNull
  private static <K, V> HashBasedIndexGenerator<K, V> getGenerator(Path chunkRoot, FileBasedIndexExtension<K, V> extension) {
    return new HashBasedIndexGenerator<>(extension, chunkRoot);
  }

  private static void deleteEmptyIndices(@NotNull List<HashBasedIndexGenerator<?, ?>> generators,
                                         @NotNull Path chunkRoot,
                                         boolean stubs) {
    Set<String> emptyIndices = new TreeSet<>();
    for (HashBasedIndexGenerator<?, ?> generator : generators) {
      if (generator.isEmpty()) {
        emptyIndices.add(generator.getExtension().getName().getName());
        Path indexRoot = generator.getIndexRoot();
        PathKt.delete(indexRoot);
      }
    }
    try {
      if (stubs) {
        EmptyIndexEnumerator.writeEmptyStubIndexes(chunkRoot, emptyIndices);
      }
      else {
        EmptyIndexEnumerator.writeEmptyIndexes(chunkRoot, emptyIndices);
      }
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to write indexes file", e);
    }
  }

  private static void zipIndexOut(@NotNull Path indexRoot, @NotNull Path zipFile, @NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    indicator.setText("Zipping index pack");

    try (JBZipFile file = new JBZipFile(zipFile.toFile())) {
      Files.walk(indexRoot).forEach(p -> {
        if (Files.isDirectory(p)) return;
        String relativePath = indexRoot.relativize(p).toString();
        try {
          JBZipEntry entry = file.getOrCreateEntry(relativePath);
          entry.setMethod(ZipEntry.STORED);
          entry.setDataFromFile(p.toFile());
        }
        catch (IOException e) {
          throw new RuntimeException("Failed to add " + relativePath + " entry to the target archive. " + e.getMessage(), e);
        }
      });
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate indexes archive at " + zipFile + ". " + e.getMessage(), e);
    }
  }

  private void generate(@NotNull IndexChunk chunk,
                        @NotNull Collection<HashBasedIndexGenerator<?, ?>> generators,
                        @NotNull Path chunkOut,
                        @NotNull ProgressIndicator indicator) {
    try {
      ContentHashEnumerator hashEnumerator = new ContentHashEnumerator(chunkOut.resolve("hashes"));
      try {
        for (HashBasedIndexGenerator<?, ?> generator : generators) {
          generator.openIndex();
        }

        indicator.setText("Collecting files to index...");
        Set<VirtualFile> files = new THashSet<>();
        for (VirtualFile root : chunk.getRoots()) {
          VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Boolean>() {
            @Override
            public boolean visitFile(@NotNull VirtualFile file) {
              if (!file.isDirectory() && !SingleRootFileViewProvider.isTooLargeForIntelligence(file)) {
                files.add(file);
              }
              return true;
            }
          });
        }

        LOG.warn("Collected " + files.size() + " files to index for " + chunk.getName());
        indicator.setText("Indexing files...");

        boolean isOk = JobLauncher.getInstance().invokeConcurrentlyUnderProgress(new ArrayList<>(files),
                                                                                 indicator,
                                                                                 f -> {
                                                                                   generateIndexesForFile(f, generators, hashEnumerator);
                                                                                   return true;
                                                                                 });

        if (!isOk) {
          throw new RuntimeException("Failed to generate indexes in parallel. JobLauncher returned false");
        }
      }
      finally {
        hashEnumerator.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      try {
        for (HashBasedIndexGenerator<?, ?> generator : generators) {
          generator.closeIndex();
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void generateIndexesForFile(@NotNull VirtualFile file,
                                      @NotNull Collection<HashBasedIndexGenerator<?, ?>> generators,
                                      @NotNull ContentHashEnumerator hashEnumerator) {
    try {
      FileContentImpl fc = (FileContentImpl)FileContentImpl.createByFile(file, myProject);
      byte[] hash = IndexedHashesSupport.getOrInitIndexedHash(fc, false);
      int hashId = Math.abs(hashEnumerator.enumerate(hash));

      for (HashBasedIndexGenerator<?, ?> generator : generators) {
        generator.indexFile(hashId, fc);
      }
    }
    catch (Exception e) {
      throw new RuntimeException("Can't index " + file.getPath(), e);
    }
  }

  private static void printStatistics(@NotNull IndexChunk chunk,
                                      @NotNull List<HashBasedIndexGenerator<?, ?>> fileBasedGenerators,
                                      @NotNull StubHashBasedIndexGenerator stubGenerator) {
    StringBuilder stats = new StringBuilder();

    for (Boolean mustBeEmpty : Arrays.asList(true, false)) {
      stats.append("Statistics for index chunk ").append(chunk.getName());
      if (mustBeEmpty) {
        stats.append(" EMPTY ONLY INDEXES ");
      } else {
        stats.append(" NON EMPTY ONLY INDEXES ");
      }
      stats.append("\n");

      stats.append("File based indices (").append(fileBasedGenerators.size()).append("):").append("\n");
      for (HashBasedIndexGenerator<?, ?> generator : fileBasedGenerators) {
        if (mustBeEmpty != (generator.getIndexedFilesNumber() == 0)) continue;
        appendGeneratorStatistics(stats, generator);
      }

      Collection<HashBasedIndexGenerator<?, ?>> stubGenerators = stubGenerator.getStubGenerators();
      stats.append("Stub indices (").append(stubGenerators.size()).append("):").append("\n");
      for (HashBasedIndexGenerator<?, ?> generator : stubGenerators) {
        if (mustBeEmpty != (generator.getIndexedFilesNumber() == 0)) continue;
        appendGeneratorStatistics(stats, generator);
      }

      stats.append("\n\n");
    }

    LOG.warn("Statistics\n" + stats);
  }

  private static void appendGeneratorStatistics(@NotNull StringBuilder stringBuilder,
                                                @NotNull HashBasedIndexGenerator<?, ?> generator) {
    stringBuilder
      .append("    ")
      .append("Generator for ")
      .append(generator.getExtension().getName().getName())
      .append(" indexed ")
      .append(generator.getIndexedFilesNumber())
      .append(" files");

    if (generator.isEmpty()) {
      stringBuilder.append(" (empty result)");
    }
    stringBuilder.append("\n");
  }
}
