package org.globsframework.shared.mem.impl.read;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.shared.mem.LongArray;
import org.globsframework.shared.mem.OffHeapNotUniqueIndex;
import org.globsframework.shared.mem.OffHeapRefs;
import org.globsframework.shared.mem.ReadOffHeapMultiIndex;
import org.globsframework.shared.mem.impl.DefaultOffHeapService;
import org.globsframework.shared.mem.impl.IndexTypeBuilder;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;
import org.globsframework.shared.mem.impl.field.dataaccess.DataAccess;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class DefaultReadOffHeapManyIndex implements ReadOffHeapMultiIndex, ReadIndex, Closeable {
    public static final VarHandle LONG_VAR_HANDLE = ValueLayout.JAVA_LONG.varHandle();
    private final OffHeapNotUniqueIndex offHeapIndex;
    private final StringAccessorByAddress stringAccessor;
    private final FileChannel indexChannel;
    private final FileChannel indexDataRefChannel;
    private final MemorySegment memorySegment;
    private final MemorySegment dataRefMemorySegment;
    private final IndexTypeBuilder indexTypeBuilder;
    private final LongArray[] arrayCaches = new LongArray[4];
    private final boolean isEmpty;
    private int firstFreeLongArrayCache = -1;

    public DefaultReadOffHeapManyIndex(Path path, OffHeapNotUniqueIndex offHeapIndex, StringAccessorByAddress stringAccessor) throws IOException {
        this.offHeapIndex = offHeapIndex;
        this.stringAccessor = stringAccessor;
        final String indexName = offHeapIndex.getName();
        indexTypeBuilder = new IndexTypeBuilder(indexName, offHeapIndex.getKeyBuilder().getFields());
        this.indexChannel = FileChannel.open(path.resolve(DefaultOffHeapService.getIndexNameFile(indexName)), StandardOpenOption.READ);
        isEmpty = indexChannel.size() == 0;
        this.indexDataRefChannel = FileChannel.open(path.resolve(DefaultOffHeapService.getIndexDataNameFile(indexName)), StandardOpenOption.READ);
        memorySegment = indexChannel.map(FileChannel.MapMode.READ_ONLY, 0, indexChannel.size(), Arena.ofShared());
        dataRefMemorySegment = indexDataRefChannel.map(FileChannel.MapMode.READ_ONLY, 0, indexDataRefChannel.size(), Arena.ofShared());
    }

    public OffHeapRefs find(FunctionalKey functionalKey) {
        if (isEmpty) {
            return OffHeapRefs.NULL;
        }
        return binSearch(functionalKey, 0);
    }

    public OffHeapRefs search(FunctionalKey functionalKey) {
        DataAccess[] handleAccesses = indexTypeBuilder.dataAccesses;
        final int lastIndex = getLastIndex(functionalKey, handleAccesses);
        Filter filter = null;
        if (lastIndex < handleAccesses.length) {
            for (int j = lastIndex; j < handleAccesses.length; j++) {
                if (functionalKey.contains(handleAccesses[j].getField()) && functionalKey.isSet(handleAccesses[j].getField())) {
                    DataAccessFilter dataAccessFilter = new DataAccessFilter(handleAccesses[j]);
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
        binSearch(functionalKey, 0, lastIndex, filter, list::add, EqualAt.BOTH);
        final long[] index = list.stream().mapToLong(l -> l).toArray();
        return new OffHeapRefs(new LongArray(index));
    }

    static int getLastIndex(FunctionalKey functionalKey, DataAccess[] handleAccesses) {
        for (int lastIndex = 0; lastIndex < handleAccesses.length; lastIndex++) {
            DataAccess handleAccess = handleAccesses[lastIndex];
            if (!functionalKey.contains(handleAccess.getField()) || !functionalKey.isSet(handleAccess.getField())) {
                return lastIndex;
            }
        }
        return handleAccesses.length;
    }

    public interface Filter {
        boolean accept(FunctionalKey functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessor);

        default Filter and(Filter filter) {
            return (functionalKey, memorySegment1, index, stringAccessor1) -> {
                return this.accept(functionalKey, memorySegment1, index, stringAccessor1) && filter.accept(functionalKey, memorySegment1, index, stringAccessor1);
            };
        }
    }

    static public class DataAccessFilter implements Filter {
        final DataAccess dataAccess;

        DataAccessFilter(DataAccess dataAccess) {
            this.dataAccess = dataAccess;
        }

        @Override
        public boolean accept(FunctionalKey functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessor) {
            return dataAccess.compare(functionalKey, memorySegment, index, stringAccessor) == 0;
        }
    }

    public interface DataIndex {
        void accept(long index);
    }

    public enum EqualAt {
        LEFT, RIGHT, BOTH
    }

    private void binSearch(FunctionalKey functionalKey, long indexOffset, int maxDepth, Filter filter, DataIndex dataIndex, EqualAt equalAt) {
        int compare = compare(functionalKey, indexOffset, maxDepth);
        if (compare == 0) {
            if (filter.accept(functionalKey, memorySegment, indexOffset, stringAccessor)) {
                int len = (int) indexTypeBuilder.dataLenOffsetArrayHandle.get(memorySegment, 0L, indexOffset);
                final long dataOffset = (long) indexTypeBuilder.dataOffsetArrayHandle.get(memorySegment, 0L, indexOffset);
                if (len == 1) {
                    dataIndex.accept(dataOffset);
                } else {
                    getDataOffset(dataOffset, len, dataIndex);
                }
            }
            {
                int index = (int) indexTypeBuilder.indexOffset1ArrayHandle.get(memorySegment, 0L, indexOffset);
                if (index != -1) {
                    binSearch(functionalKey, index, maxDepth, filter, dataIndex, EqualAt.RIGHT);
                }
            }
            {
                int index = (int) indexTypeBuilder.indexOffset2ArrayHandle.get(memorySegment, 0L, indexOffset);
                if (index != -1) {
                    binSearch(functionalKey, index, maxDepth, filter, dataIndex, EqualAt.LEFT);
                }
            }
        } else {
            if (equalAt == EqualAt.LEFT) {
                if (compare < 0) {
                    int index = (int) indexTypeBuilder.indexOffset1ArrayHandle.get(memorySegment, 0L, indexOffset);
                    if (index >= 0) {
                        binSearch(functionalKey, index, maxDepth, filter, dataIndex, EqualAt.BOTH);
                    }
                }
                else {
                    return;
                }
            } else if (equalAt == EqualAt.RIGHT) {
                if (compare > 0) {
                    int index = (int) indexTypeBuilder.indexOffset2ArrayHandle.get(memorySegment, 0L, indexOffset);
                    if (index >= 0) {
                        binSearch(functionalKey, index, maxDepth, filter, dataIndex, EqualAt.BOTH);
                    }
                } else {
                    return;
                }
            } else if (equalAt == EqualAt.BOTH) {
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


    private OffHeapRefs binSearch(FunctionalKey functionalKey, long indexOffset) {
        int compare = compare(functionalKey, indexOffset);
        if (compare == 0) {
            int len = (int) indexTypeBuilder.dataLenOffsetArrayHandle.get(memorySegment, 0L, indexOffset);
            final long dataOffset = (long) indexTypeBuilder.dataOffsetArrayHandle.get(memorySegment, 0L, indexOffset);
            if (len == 1) {
                final LongArray arrays = getArrays(1);
                arrays.getOffset()[0] = dataOffset;
                return new OffHeapRefs(arrays);
            } else {
                return new OffHeapRefs(getDataOffset(dataOffset, len));
            }
        }
        if (compare < 0) {
            int index = (int) indexTypeBuilder.indexOffset1ArrayHandle.get(memorySegment, 0L, indexOffset);
            if (index >= 0) {
                return binSearch(functionalKey, index);
            } else {
                return OffHeapRefs.NULL;
            }
        } else {
            int index = (int) indexTypeBuilder.indexOffset2ArrayHandle.get(memorySegment, 0L, indexOffset);
            if (index >= 0) {
                return binSearch(functionalKey, index);
            } else {
                return OffHeapRefs.NULL;
            }
        }
    }

    public void free(OffHeapRefs offHeapRefs) {
        if (firstFreeLongArrayCache < arrayCaches.length) {
            final LongArray offset = offHeapRefs.offset();
            offset.free();
            arrayCaches[++firstFreeLongArrayCache] = offset;
        }
    }

    LongArray getArrays(int size) {
        if (firstFreeLongArrayCache < 0) {
            return new LongArray(new long[size]);
        } else {
            final LongArray arrayCach = arrayCaches[firstFreeLongArrayCache--];
            arrayCach.take(size);
            return arrayCach;
        }
    }

    private LongArray getDataOffset(long dataOffset, int size) {
        LongArray offsets = getArrays(size);
        final long[] offset = offsets.getOffset();
        for (int i = 0; i < size; i++) {
            offset[i] = ((long) LONG_VAR_HANDLE.get(dataRefMemorySegment, dataOffset + i * 8L));
        }
        return offsets;
    }

    private void getDataOffset(long dataOffset, int size, DataIndex dataIndex) {
        for (int i = 0; i < size; i++) {
            dataIndex.accept(((long) LONG_VAR_HANDLE.get(dataRefMemorySegment, dataOffset + i * 8L)));
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

    public boolean isUnique() {
        return false;
    }

    @Override
    public void close() throws IOException {
        try {
            indexChannel.close();
        } catch (IOException e) {
        }
        indexDataRefChannel.close();
    }
}
