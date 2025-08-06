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

public class DefaultReadOffHeapUniqueIndex implements ReadOffHeapUniqueIndex, ReadIndex, AutoCloseable {
    private final OffHeapUniqueIndex offHeapIndex;
    private final StringAccessorByAddress stringAccessor;
    private final FileChannel indexChannel;
    private final MemorySegment memorySegment;
    private final IndexTypeBuilder indexTypeBuilder;

    public DefaultReadOffHeapUniqueIndex(Path path, OffHeapUniqueIndex offHeapIndex, StringAccessorByAddress stringAccessor) throws IOException {
        this.offHeapIndex = offHeapIndex;
        this.stringAccessor = stringAccessor;
        final String indexName = offHeapIndex.getName();
        indexTypeBuilder = new IndexTypeBuilder(indexName, offHeapIndex.getKeyBuilder().getFields());
        this.indexChannel = FileChannel.open(path.resolve(DefaultOffHeapService.getIndexNameFile(indexName)), StandardOpenOption.READ);
        memorySegment = indexChannel.map(FileChannel.MapMode.READ_ONLY, 0, indexChannel.size(), Arena.ofShared());
    }

    public OffHeapRef find(FunctionalKey functionalKey) {
        return binSearch(functionalKey, 0);
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
                return null;
            }
        } else {
            int index = (int) indexTypeBuilder.indexOffset2ArrayHandle.get(memorySegment, 0L, indexOffset);
            if (index >= 0) {
                return binSearch(functionalKey, index);
            } else {
                return null;
            }
        }
    }

    private int compare(FunctionalKey functionalKey, long index) {
        DataAccess[] handleAccesses = indexTypeBuilder.dataAccesses;
        for (int i = 0; i < handleAccesses.length; i++) {
            DataAccess handleAccess = handleAccesses[i];
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
