// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.hash.building;

import com.google.common.collect.Maps;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.EmptyInputDataDiffBuilder;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.UpdateData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StubHashBasedIndexGenerator extends HashBasedIndexGenerator<Integer, SerializedStubTree> {

  private final Map<StubIndexKey<?, ?>, HashBasedIndexGenerator<?, ?>> myStubIndexesGeneratorMap = new HashMap<>();

  private final Path myStubIndicesRoot;

  private StubHashBasedIndexGenerator(@NotNull StubUpdatingIndex index,
                                      @NotNull Path stubIndicesRoot,
                                      @NotNull List<StubIndexExtension<?, ?>> stubIndexExtensions) {
    super(index, stubIndicesRoot.getParent(), true);
    myStubIndicesRoot = stubIndicesRoot;

    for (StubIndexExtension<?, ?> stubIndexExtension : stubIndexExtensions) {
      myStubIndexesGeneratorMap.put(stubIndexExtension.getKey(), createGenerator(stubIndexExtension));
    }
  }

  @NotNull
  public static StubHashBasedIndexGenerator create(@NotNull Path stubIndicesRoot,
                                                   @NotNull SerializationManagerEx serializationManager,
                                                   @NotNull List<StubIndexExtension<?, ?>> stubIndexExtensions) {
    StubForwardIndexExternalizer<?> forwardIndexExternalizer = StubForwardIndexExternalizer.createFileLocalExternalizer(serializationManager);
    return new StubHashBasedIndexGenerator(
      new StubUpdatingIndex(forwardIndexExternalizer, serializationManager),
      stubIndicesRoot,
      stubIndexExtensions
    );
  }

  @NotNull
  private <K, V extends PsiElement> HashBasedIndexGenerator<K, Void> createGenerator(StubIndexExtension<K, V> stubIndexExtension) {
    FileBasedIndexExtension<K, Void> extension = StubIndexImpl.wrapStubIndexExtension(stubIndexExtension);
    return new HashBasedIndexGenerator(extension, myStubIndicesRoot, false);
  }

  @NotNull
  public List<HashBasedIndexGenerator<?, ?>> getStubGenerators() {
    return new ArrayList<>(myStubIndexesGeneratorMap.values());
  }

  @Override
  protected void visitInputData(int hashId, @NotNull InputData<Integer, SerializedStubTree> data) throws StorageException {
    super.visitInputData(hashId, data);
    SerializedStubTree tree = ContainerUtil.getFirstItem(data.getKeyValues().values());
    if (tree == null) return;
    Map<StubIndexKey, Map<Object, StubIdList>> map = tree.getStubIndicesValueMap();
    for (Map.Entry<StubIndexKey, Map<Object, StubIdList>> entry : map.entrySet()) {
      StubIndexKey key = entry.getKey();
      Map<Object, StubIdList> value = entry.getValue();
      MapReduceIndex index = (MapReduceIndex)myStubIndexesGeneratorMap.get(key).getIndex();
      Map<Object, Object> reducedValue = Maps.asMap(value.keySet(), k -> null);
      UpdateData<?, ?> updateData = new UpdateData(
        hashId,
        reducedValue,
        () -> new EmptyInputDataDiffBuilder(hashId),
        index.getExtension().getName(),
        null
      );
      index.updateWithMap(updateData);
    }
  }

  @Override
  public void openIndex() throws IOException {
    super.openIndex();
    for (HashBasedIndexGenerator<?, ?> generator : myStubIndexesGeneratorMap.values()) {
      generator.openIndex();
    }
  }

  @Override
  public void closeIndex() throws IOException {
    super.closeIndex();
    for (Map.Entry<StubIndexKey<?, ?>, HashBasedIndexGenerator<?, ?>> entry : myStubIndexesGeneratorMap.entrySet()) {
      entry.getValue().closeIndex();
    }
  }
}
