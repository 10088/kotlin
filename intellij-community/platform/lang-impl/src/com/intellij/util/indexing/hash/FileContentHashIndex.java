// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.hash;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.ValueContainerInputRemapping;
import com.intellij.util.indexing.impl.forward.MapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

public class FileContentHashIndex extends VfsAwareMapReduceIndex<Long, Void> {
  private static final Logger LOG = Logger.getInstance(FileContentHashIndex.class);

  public FileContentHashIndex(@NotNull FileContentHashIndexExtension extension, IndexStorage<Long, Void> storage) throws IOException {
    super(extension,
          storage,
          new PersistentMapBasedForwardIndex(IndexInfrastructure.getInputIndexStorageFile(extension.getName()).toPath(), false),
          new MapForwardIndexAccessor<>(new InputMapExternalizer<>(extension)), null, null);
  }

  @NotNull
  @Override
  protected Computable<Boolean> createIndexUpdateComputation(@NotNull AbstractUpdateData<Long, Void> updateData) {
    return new HashIndexUpdateComputable(super.createIndexUpdateComputation(updateData), updateData.newDataIsEmpty());
  }

  public int getAssociatedChunkId(int fileId, VirtualFile file) {
    try {
      return FileContentHashIndexExtension.getIndexId(getHashId(fileId));
    }
    catch (StorageException e) {
      LOG.error(e);
      FileBasedIndex.getInstance().requestReindex(file);
      return FileContentHashIndexExtension.NULL_INDEX_ID;
    }
  }

  public long getHashId(int fileId) throws StorageException {
    Map<Long, Void> data = getIndexedFileData(fileId);
    if (data.isEmpty()) return FileContentHashIndexExtension.NULL_HASH_ID;
    return data.keySet().iterator().next();
  }

  @NotNull
  public ValueContainerInputRemapping getHashIdToFileIdsFunction(int indexId) {
    return hash -> {
      try {
        ValueContainer<Void> data = getData(FileContentHashIndexExtension.getHashId(hash, indexId));
        if (data.size() == 0) return ArrayUtil.EMPTY_INT_ARRAY;
        return collect(data.getValueIterator().getInputIdsIterator());
      }
      catch (StorageException e) {
        throw new RuntimeException(e);
      }
    };
  }

  final static class HashIndexUpdateComputable implements Computable<Boolean> {
    @NotNull
    private final Computable<Boolean> myUnderlying;
    private final boolean myEmptyInput;

    HashIndexUpdateComputable(@NotNull Computable<Boolean> underlying, boolean isEmptyInput) {myUnderlying = underlying;
      myEmptyInput = isEmptyInput;
    }

    boolean isEmptyInput() {
      return myEmptyInput;
    }

    @Override
    public Boolean compute() {
      return myUnderlying.compute();
    }
  }

  private static int[] collect(@NotNull ValueContainer.IntIterator intIterator) {
    int[] result = new int[intIterator.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = intIterator.next();
    }
    return result;
  }
}
