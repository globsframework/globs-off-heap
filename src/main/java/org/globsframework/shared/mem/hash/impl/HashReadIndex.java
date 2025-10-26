package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.OffHeapGlobTypeGroupLayoutImpl;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
//            final HashMap<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap = new HashMap<>();
//            final OffHeapGlobTypeGroupLayoutImpl offHeapGlobTypeGroupLayout = OffHeapGlobTypeGroupLayoutImpl.create(HashWriteIndex.PerData.TYPE);
            this.count = Math.toIntExact(indexChannel.size() / byteSizeWithPadding);
            arena = Arena.ofShared();
            memorySegment = indexChannel.map(FileChannel.MapMode.READ_ONLY,
                    0,
                    count * byteSizeWithPadding, arena);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Glob getAt(FunctionalKey functionalKey, ReadContext readContext) {
        int h = HashWriteIndex.hash(functionalKey);
        final int tableIndex = HashWriteIndex.tableIndex(h, tableSize);

        long offset = tableIndex * byteSizeWithPadding;
        while (true) {
            int hash = (int) hashVarHandle.get(memorySegment, offset);
            if (hash == h) {
                if ((boolean) isValidVarHandle.get(memorySegment, offset)) {
                    Glob read = readAndCheck(functionalKey, (long) dataIndexVarHandle.get(memorySegment, offset), readContext);
                    if (read != null) {
                        return read;
                    }
                }
            }
            final int nextIndex = (int) nextIndexVarHandle.get(memorySegment, offset);
            if (nextIndex <= 0) {
                return null;
            }
            offset = nextIndex * byteSizeWithPadding;
        }
    }

    private Glob readAndCheck(FunctionalKey functionalKey, long offset, ReadContext readContext) {
        final Glob read = readContext.read(offset);
        if (read != null) {
            for (Field field : functionalKey.getBuilder().getFields()) {
                if (!field.valueEqual(functionalKey.getValue(field), read.getValue(field))) {
                    return null;
                }
            }
        }
        return read;
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
