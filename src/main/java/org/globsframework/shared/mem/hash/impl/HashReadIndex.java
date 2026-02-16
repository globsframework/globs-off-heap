package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.DataConsumer;
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
import java.util.function.Predicate;

public class HashReadIndex {
    public static final long byteSizeWithPadding;
    public static final long byteSize;
    public static final VarHandle hashVarHandle;
    public static final VarHandle nextIndexVarHandle;
    public static final VarHandle dataIndexVarHandle;
    public static final VarHandle isValidVarHandle;
    private final FileChannel indexChannel;
    private final int count;
    private final Arena arena;
    private final MemorySegment memorySegment;
    private final HashIndex hashIndex;

    public HashReadIndex(HashIndex hashIndex, Path path) {
        this.hashIndex = hashIndex;
        try {
            this.indexChannel = FileChannel.open(
                    path.resolve(DefaultOffHeapTreeService.createContentFileName(HashWriteIndex.PerData.TYPE, hashIndex.name())), StandardOpenOption.READ);
            this.count = Math.toIntExact(indexChannel.size() / byteSizeWithPadding);
            arena = Arena.ofShared();
            memorySegment = indexChannel.map(FileChannel.MapMode.READ_ONLY,
                    0,
                    count * byteSizeWithPadding, arena);
            memorySegment.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Glob getAt(FunctionalKey functionalKey, ReadContext readContext) {
        int h = HashWriteIndex.hash(functionalKey);
        final int tableIndex = HashWriteIndex.tableIndex(h, hashIndex.size());

        long offset = tableIndex * byteSizeWithPadding;
        while (true) {
            int hash = (int) hashVarHandle.get(memorySegment, offset);
            if (hash == h) {
                if ((int) isValidVarHandle.get(memorySegment, offset) == 1) {
                    Glob read = readAndCheck(functionalKey, (long) dataIndexVarHandle.get(memorySegment, offset), readContext, field -> true);
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

    public void readAll(DataConsumer consumer, ReadContext readContext) {
        for (int i = 0; i < count; i++) {
            long offset = i *  byteSizeWithPadding;
            if ((int) isValidVarHandle.get(memorySegment, offset) == 1) {
                final Glob read = readContext.read((long) dataIndexVarHandle.get(memorySegment, offset), field -> true);
                consumer.accept(read);
            }
        }
    }

    public static Glob readAndCheck(FunctionalKey functionalKey, long offset, ReadContext readContext, Predicate<Field> contains) {
        final Glob read = readContext.read(offset, contains);
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
        final GroupLayout groupLayout = offHeapGlobTypeGroupLayout.getPrimaryGroupLayout();
        byteSizeWithPadding = OffHeapTypeInfo.computeSizeWithPadding(groupLayout);
        byteSize = groupLayout.byteSize();
        hashVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(HashWriteIndex.PerData.hash.getName()));
        nextIndexVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(HashWriteIndex.PerData.nextIndex.getName()));
        dataIndexVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(HashWriteIndex.PerData.dataIndex.getName()));
        isValidVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(HashWriteIndex.PerData.isValid.getName()));
    }
}
