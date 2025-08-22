package org.globsframework.shared.mem.impl.read;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.metamodel.fields.Field;
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
