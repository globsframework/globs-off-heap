package org.globsframework.shared.mem.impl.read;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.shared.mem.*;
import org.globsframework.shared.mem.impl.DefaultOffHeapService;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;
import org.globsframework.shared.mem.impl.IndexTypeBuilder;
import org.globsframework.shared.mem.impl.field.dataaccess.DataAccess;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.globsframework.shared.mem.impl.read.DefaultReadOffHeapManyIndex.getLastIndex;

public class DefaultReadOffHeapUniqueIndex implements ReadOffHeapUniqueIndex, ReadIndex, AutoCloseable {
    private final OffHeapUniqueIndex offHeapIndex;
    private final StringAccessorByAddress stringAccessor;
    private final FileChannel indexChannel;
    private final MemorySegment memorySegment;
    private final IndexTypeBuilder indexTypeBuilder;
    private final boolean isEmpty;

    public DefaultReadOffHeapUniqueIndex(Path path, OffHeapUniqueIndex offHeapIndex, StringAccessorByAddress stringAccessor) throws IOException {
        this.offHeapIndex = offHeapIndex;
        this.stringAccessor = stringAccessor;
        final String indexName = offHeapIndex.getName();
        indexTypeBuilder = new IndexTypeBuilder(indexName, offHeapIndex.getKeyBuilder().getFields());
        this.indexChannel = FileChannel.open(path.resolve(DefaultOffHeapService.getIndexNameFile(indexName)), StandardOpenOption.READ);
        final long size = indexChannel.size();
        isEmpty = size == 0;
        memorySegment = indexChannel.map(FileChannel.MapMode.READ_ONLY, 0, size, Arena.ofShared());
    }

    public OffHeapRef find(FunctionalKey functionalKey) {
        if (isEmpty) {
            return OffHeapRef.NULL;
        }
        return binSearch(functionalKey, 0);
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

    private void binSearch(FunctionalKey functionalKey, long indexOffset, int maxDepth, DefaultReadOffHeapManyIndex.Filter filter, DefaultReadOffHeapManyIndex.DataIndex dataIndex, DefaultReadOffHeapManyIndex.EqualAt equalAt) {
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


    private OffHeapRef binSearch(FunctionalKey functionalKey, long indexOffset) {
        int compare = compare(functionalKey, indexOffset);
        if (compare == 0) {
            return new OffHeapRef((long) indexTypeBuilder.dataOffsetArrayHandle.get(memorySegment, 0L, indexOffset));
        }
        if (compare < 0) {
            int index = (int) indexTypeBuilder.indexOffset1ArrayHandle.get(memorySegment, 0L, indexOffset);
            if (index >= 0) {
                return binSearch(functionalKey, index);
            } else {
                return OffHeapRef.NULL;
            }
        } else {
            int index = (int) indexTypeBuilder.indexOffset2ArrayHandle.get(memorySegment, 0L, indexOffset);
            if (index >= 0) {
                return binSearch(functionalKey, index);
            } else {
                return OffHeapRef.NULL;
            }
        }
    }

    private int compare(FunctionalKey functionalKey, long index) {
        DataAccess[] handleAccesses = indexTypeBuilder.dataAccesses;
        for (int i = 0; i < handleAccesses.length; i++) {
            DataAccess handleAccess = handleAccesses[i];
            if (!functionalKey.isSet(handleAccess.getField())) {
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

    public boolean isUnique() {
        return true;
    }

    public void close() throws Exception {
        indexChannel.close();
    }
}
