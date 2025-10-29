package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.DefaultOffHeapReadDataService;
import org.globsframework.shared.mem.tree.impl.*;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.read.TypeSegment;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Map;

public class Updater {
    private final FunctionalKeyBuilder keyBuilder;
    final Path directory;
    private final long maxElement;
    private TypeSegment typeSegment;
    private final int tableSize;

    public Updater(HashIndex hashIndex, Path directory, Arena arena) throws IOException {
        this.keyBuilder = hashIndex.keyBuilder();
        this.directory = directory;
        this.tableSize = hashIndex.size();
        OffHeapGlobTypeGroupLayout offHeapGlobTypeGroupLayout = OffHeapGlobTypeGroupLayoutImpl.create(HashWriteIndex.PerData.TYPE);
        final RootOffHeapTypeInfo rootOffHeapTypeInfo = new RootOffHeapTypeInfo(OffHeapTypeInfo.create(HashWriteIndex.PerData.TYPE, offHeapGlobTypeGroupLayout.getPrimaryGroupLayout()), Map.of());

        typeSegment = DefaultOffHeapReadDataService.loadMemorySegment(arena,
                FileChannel.MapMode.READ_WRITE, 0, rootOffHeapTypeInfo.primary(),
                directory.resolve(DefaultOffHeapTreeService.createContentFileName(HashWriteIndex.PerData.TYPE, hashIndex.name())));
        maxElement = typeSegment.fileChannel().size() / HashReadIndex.byteSizeWithPadding;
    }

    public long save(Glob data, long dataOffset, ReadContext readContext) {
        final FunctionalKey functionalKey = keyBuilder.create(data);
        int h = HashWriteIndex.hash(functionalKey);
        final int tableIndex = HashWriteIndex.tableIndex(h, tableSize);
        long previousDataOffset;
        if ((previousDataOffset = updateIfFound(readContext, tableIndex, h, functionalKey, dataOffset)) != -1) {
            return previousDataOffset;
        }
        long offset = tableIndex * HashReadIndex.byteSizeWithPadding;
        int previousNext = 0;
        int nextIndex;
        while (true) {
            if ((int) HashReadIndex.isValidVarHandle.get(typeSegment.segment(), offset) != 1) {
                HashReadIndex.dataIndexVarHandle.set(typeSegment.segment(), offset, dataOffset);
                HashReadIndex.hashVarHandle.set(typeSegment.segment(), offset, h);
                HashReadIndex.nextIndexVarHandle.set(typeSegment.segment(), offset, previousNext);
                HashReadIndex.isValidVarHandle.setRelease(typeSegment.segment(), offset, 1);
                return previousDataOffset;
            }
            nextIndex = (int) HashReadIndex.nextIndexVarHandle.get(typeSegment.segment(), offset);
            if (nextIndex <= 0) {
                break;
            }
            offset = nextIndex * HashReadIndex.byteSizeWithPadding;
        }
        {
            int freeOffset = findFreePosition();

            HashReadIndex.nextIndexVarHandle.set(typeSegment.segment(), freeOffset, 0);
            HashReadIndex.dataIndexVarHandle.set(typeSegment.segment(), freeOffset, dataOffset);
            HashReadIndex.hashVarHandle.set(typeSegment.segment(), freeOffset, h);
            HashReadIndex.isValidVarHandle.set(typeSegment.segment(), freeOffset, 1);

            HashReadIndex.nextIndexVarHandle.setRelease(typeSegment.segment(), offset, freeOffset);
        }
        return previousDataOffset;
    }

    private long updateIfFound(ReadContext readContext, int tableIndex, int h, FunctionalKey functionalKey, long dataOffset) {
        long offset = tableIndex * HashReadIndex.byteSizeWithPadding;
        while (true) {
            final MemorySegment segment = typeSegment.segment();
            int hash = (int) HashReadIndex.hashVarHandle.get(segment, offset);
            if (hash == h) {
                if ((int) HashReadIndex.isValidVarHandle.get(segment, offset) == 1) {
                    final long previousDataOffset = (long) HashReadIndex.dataIndexVarHandle.get(segment, offset);
                    Glob read = HashReadIndex.readAndCheck(functionalKey,
                            previousDataOffset, readContext, functionalKey::contains);
                    if (read != null) {
                        HashReadIndex.dataIndexVarHandle.set(segment, offset, dataOffset);
                        return previousDataOffset;
                    }
                }
            }
            final int nextIndex = (int) HashReadIndex.nextIndexVarHandle.get(segment, offset);
            if (nextIndex <= 0) {
                break;
            }
            offset = nextIndex * HashReadIndex.byteSizeWithPadding;
        }
        return -1;
    }

    private int findFreePosition() {
        int offset = tableSize;
        final MemorySegment segment = typeSegment.segment();
        while (maxElement < offset) {
            if ((int) HashReadIndex.isValidVarHandle.get(segment, offset * HashReadIndex.byteSizeWithPadding) == 2) {
                return offset;
            }
            offset++;
        }
        throw new RuntimeException("No free position found");
    }
}
