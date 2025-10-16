package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.OffHeapGlobTypeGroupLayoutImpl;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;

public class HashReadIndex {
    private static final long byteSizeWithPadding;
    private static final long byteSize;
    private static final VarHandle hashVarHandle;
    private static final VarHandle nextIndexVarHandle;
    private static final VarHandle dataIndexVarHandle;
    private static final VarHandle isValidVarHandle;
    private final int tableSize;
    private final FileChannel indexChannel;
    private final int count;
    private final Arena arena;
    private final MemorySegment memorySegment;

    public HashReadIndex(int tableSize, Path path) {
        this.tableSize = tableSize;
        try {
            this.indexChannel = FileChannel.open(path.resolve(DefaultOffHeapTreeService.createContentFileName(HashWriteIndex.PerData.TYPE)), StandardOpenOption.READ);
            final HashMap<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap = new HashMap<>();
            final OffHeapGlobTypeGroupLayoutImpl offHeapGlobTypeGroupLayout = OffHeapGlobTypeGroupLayoutImpl.create(HashWriteIndex.PerData.TYPE);
            this.count = Math.toIntExact(indexChannel.size() / byteSizeWithPadding);
            arena = Arena.ofShared();
            memorySegment = indexChannel.map(FileChannel.MapMode.READ_ONLY,
                    0,
                    count * byteSizeWithPadding, arena);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public long getAt(FunctionalKey functionalKey) {
        int h = HashWriteIndex.hash(functionalKey);
        final int tableIndex = HashWriteIndex.tableIndex(h, tableSize);

        long offset = tableIndex * byteSizeWithPadding;
        while (true) {
            int hash = (int) hashVarHandle.get(memorySegment, offset);
            if (hash == h) {
                if ((boolean) isValidVarHandle.get(memorySegment, offset)) {
                    return (long) dataIndexVarHandle.get(memorySegment, offset);
                }
            }
            final int nextIndex = (int) nextIndexVarHandle.get(memorySegment, offset);
            if (nextIndex <= 0) {
                return -1;
            }
            offset = nextIndex * byteSizeWithPadding;
        }
    }

    static {
        final OffHeapGlobTypeGroupLayoutImpl offHeapGlobTypeGroupLayout = OffHeapGlobTypeGroupLayoutImpl.create(HashWriteIndex.PerData.TYPE);
        final GroupLayout groupLayout = offHeapGlobTypeGroupLayout.getGroupLayout(HashWriteIndex.PerData.TYPE);
        byteSizeWithPadding = OffHeapTypeInfo.computeSizeWithPadding(groupLayout);
        byteSize = groupLayout.byteSize();
        hashVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(HashWriteIndex.PerData.hash.getName()));
        nextIndexVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(HashWriteIndex.PerData.nextIndex.getName()));
        dataIndexVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(HashWriteIndex.PerData.dataIndex.getName()));
        isValidVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(HashWriteIndex.PerData.isValid.getName()));
    }
}
