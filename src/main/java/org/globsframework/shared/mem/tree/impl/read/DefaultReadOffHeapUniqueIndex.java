package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.StringAccessorByAddress;
import org.globsframework.shared.mem.tree.impl.IndexTypeBuilder;
import org.globsframework.shared.mem.field.dataaccess.DataAccess;
import org.globsframework.shared.mem.tree.*;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.globsframework.shared.mem.tree.impl.read.DefaultReadOffHeapManyIndex.getLastIndex;

public class DefaultReadOffHeapUniqueIndex implements ReadOffHeapUniqueIndex, ReadIndex, AutoCloseable {
    private final OffHeapUniqueIndex offHeapIndex;
    private final StringAccessorByAddress stringAccessor;
    private final FileChannel indexChannel;
    private final MemorySegment memorySegment;
    private final IndexTypeBuilder indexTypeBuilder;
    private final boolean isEmpty;
    private final int maxIndex;

    public DefaultReadOffHeapUniqueIndex(Path path, OffHeapUniqueIndex offHeapIndex, StringAccessorByAddress stringAccessor) throws IOException {
        this.offHeapIndex = offHeapIndex;
        this.stringAccessor = stringAccessor;
        final String indexName = offHeapIndex.getName();
        indexTypeBuilder = new IndexTypeBuilder(indexName, offHeapIndex.getKeyBuilder().getFields());
        this.indexChannel = FileChannel.open(path.resolve(DefaultOffHeapTreeService.getIndexNameFile(indexName)), StandardOpenOption.READ);
        final long size = indexChannel.size();
        isEmpty = size == 0;
        memorySegment = indexChannel.map(FileChannel.MapMode.READ_ONLY, 0, size, Arena.ofShared());
        maxIndex = Math.toIntExact(size / indexTypeBuilder.offHeapIndexTypeInfo.byteSizeWithPadding());
    }

    public OffHeapRef find(FunctionalKey functionalKey) {
        if (isEmpty) {
            return OffHeapRef.NULL;
        }
        checkKey(functionalKey, indexTypeBuilder.dataAccesses);
        return binSearch(functionalKey);
    }

    public OffHeapRefs search(FunctionalKey functionalKey) {
        DataAccess[] handleAccesses = indexTypeBuilder.dataAccesses;
        final int lastIndex = getLastIndex(functionalKey, handleAccesses);
        DefaultReadOffHeapManyIndex.Filter filter = null;
        if (lastIndex < handleAccesses.length) {
            for (int j = lastIndex; j < handleAccesses.length; j++) {
                if (functionalKey.contains(handleAccesses[j].getField()) && functionalKey.isSet(handleAccesses[j].getField())) {
                    DefaultReadOffHeapManyIndex.DataAccessFilter dataAccessFilter = new DefaultReadOffHeapManyIndex.DataAccessFilter(handleAccesses[j]);
                    filter = filter == null ? dataAccessFilter : filter.and(dataAccessFilter);
                }
            }
            if (filter == null) {
                filter = (functionalKey1, memorySegment1, index, stringAccessor1) -> true;
            }
        } else {
            filter = (functionalKey1, memorySegment1, index, stringAccessor1) -> true;
        }
        // scan all with filter.
        List<Long> list = new java.util.ArrayList<>();
        binSearch(functionalKey, 0, lastIndex, filter, list::add, DefaultReadOffHeapManyIndex.EqualAt.BOTH);
        final long[] index = list.stream().mapToLong(l -> l).toArray();
        return new OffHeapRefs(new LongArray(index));
    }

    private int compare(FunctionalKey functionalKey, long index, int maxDepth) {
        DataAccess[] handleAccesses = indexTypeBuilder.dataAccesses;
        for (int i = 0; i < maxDepth; i++) {
            DataAccess handleAccess = handleAccesses[i];
            if (!functionalKey.contains(handleAccess.getField()) || !functionalKey.isSet(handleAccess.getField())) {
                throw new IllegalArgumentException("FunctionalKey must contain all fields of the index: "
                                                   + offHeapIndex.getName() + " " + functionalKey + " for field " + handleAccess.getField());
            }
            int cmp = handleAccess.compare(functionalKey, memorySegment, index, stringAccessor);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private void binSearch(FunctionalKey functionalKey, long indexOffset, int maxDepth, DefaultReadOffHeapManyIndex.Filter filter,
                           DefaultReadOffHeapManyIndex.DataIndex dataIndex, DefaultReadOffHeapManyIndex.EqualAt equalAt) {
        int compare = compare(functionalKey, indexOffset, maxDepth);
        if (compare == 0) {
            if (filter.accept(functionalKey, memorySegment, indexOffset, stringAccessor)) {
                final long dataOffset = (long) indexTypeBuilder.dataOffsetArrayHandle.get(memorySegment, 0L, indexOffset);
                dataIndex.accept(dataOffset);
            }
            {
                int index = (int) indexTypeBuilder.indexOffset1ArrayHandle.get(memorySegment, 0L, indexOffset);
                if (index != -1) {
                    binSearch(functionalKey, index, maxDepth, filter, dataIndex, DefaultReadOffHeapManyIndex.EqualAt.RIGHT);
                }
            }
            {
                int index = (int) indexTypeBuilder.indexOffset2ArrayHandle.get(memorySegment, 0L, indexOffset);
                if (index != -1) {
                    binSearch(functionalKey, index, maxDepth, filter, dataIndex, DefaultReadOffHeapManyIndex.EqualAt.LEFT);
                }
            }
        } else {
            if (equalAt == DefaultReadOffHeapManyIndex.EqualAt.LEFT) {
                if (compare < 0) {
                    int index = (int) indexTypeBuilder.indexOffset1ArrayHandle.get(memorySegment, 0L, indexOffset);
                    if (index >= 0) {
                        binSearch(functionalKey, index, maxDepth, filter, dataIndex, DefaultReadOffHeapManyIndex.EqualAt.BOTH);
                    }
                }
                else {
                    return;
                }
            } else if (equalAt == DefaultReadOffHeapManyIndex.EqualAt.RIGHT) {
                if (compare > 0) {
                    int index = (int) indexTypeBuilder.indexOffset2ArrayHandle.get(memorySegment, 0L, indexOffset);
                    if (index >= 0) {
                        binSearch(functionalKey, index, maxDepth, filter, dataIndex, DefaultReadOffHeapManyIndex.EqualAt.BOTH);
                    }
                } else {
                    return;
                }
            } else if (equalAt == DefaultReadOffHeapManyIndex.EqualAt.BOTH) {
                if (compare > 0) {
                    int index = (int) indexTypeBuilder.indexOffset2ArrayHandle.get(memorySegment, 0L, indexOffset);
                    if (index >= 0) {
                        binSearch(functionalKey, index, maxDepth, filter, dataIndex, equalAt);
                    }
                } else {
                    int index = (int) indexTypeBuilder.indexOffset1ArrayHandle.get(memorySegment, 0L, indexOffset);
                    if (index >= 0) {
                        binSearch(functionalKey, index, maxDepth, filter, dataIndex, equalAt);
                    }
                }
            }
        }
    }

    private OffHeapRef binSearch(FunctionalKey functionalKey) {
        final VarHandle dataOffsetArrayHandle = indexTypeBuilder.dataOffsetArrayHandle;
        final VarHandle indexOffset1ArrayHandle = indexTypeBuilder.indexOffset1ArrayHandle;
        final VarHandle indexOffset2ArrayHandle = indexTypeBuilder.indexOffset2ArrayHandle;
        final MemorySegment memSeg = memorySegment;
        final DataAccess[] dataAccesses = indexTypeBuilder.dataAccesses;
        final StringAccessorByAddress stringAccessor = this.stringAccessor;
        long indexOffset = 0;
        while (true) {
            int compare = compare(functionalKey, indexOffset, dataAccesses, memSeg, stringAccessor);
            if (compare == 0) {
                return new OffHeapRef((long) dataOffsetArrayHandle.get(memSeg, 0L, indexOffset));
            }
            if (compare < 0) {
                int index = (int) indexOffset1ArrayHandle.get(memSeg, 0L, indexOffset);
                if (index >= 0) {
                    indexOffset = index;
                } else {
                    return OffHeapRef.NULL;
                }
            } else {
                int index = (int) indexOffset2ArrayHandle.get(memSeg, 0L, indexOffset);
                if (index >= 0) {
                    indexOffset = index;
                } else {
                    return OffHeapRef.NULL;
                }
            }
        }
    }

    public void warmup() {
        int index = 0;
        final MemorySegment ms = memorySegment;
        final VarHandle indexHandle = indexTypeBuilder.indexOffset1ArrayHandle;
        while (index < maxIndex) {
            indexHandle.get(ms, 0L, index);
            index++;
        }
    }

    private void checkKey(FunctionalKey functionalKey, DataAccess[] handleAccesses) {
        for (DataAccess handleAccess : handleAccesses) {
            if (!functionalKey.isSet(handleAccess.getField())) {
                throw new IllegalArgumentException("FunctionalKey must contain all fields of the index: "
                                                   + offHeapIndex.getName() + " " + functionalKey + " for field " + handleAccess.getField());
            }
        }
    }

    private static int compare(FunctionalKey functionalKey, long index,
                        DataAccess[] handleAccesses, MemorySegment memSeg, StringAccessorByAddress stringAccessor) {
        for (int i = 0; i < handleAccesses.length; i++) {
            int cmp = handleAccesses[i].compare(functionalKey, memSeg, index, stringAccessor);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    public boolean isUnique() {
        return true;
    }

    public void close() throws Exception {
        indexChannel.close();
    }
}
