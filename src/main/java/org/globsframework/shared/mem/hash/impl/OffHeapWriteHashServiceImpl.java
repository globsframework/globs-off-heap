package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.DataSaver;
import org.globsframework.shared.mem.OffsetHeader;
import org.globsframework.shared.mem.hash.OffHeapWriteHashService;

import java.io.IOException;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;

import static org.globsframework.shared.mem.hash.impl.OffHeapHashServiceImpl.HEADER_SIZE;

class OffHeapWriteHashServiceImpl implements OffHeapWriteHashService {
    private final Path directory;
    private final GlobType type;
    private final HashSet<GlobType> typeToSave;
    private final Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap;
    private final List<HashIndex> index;
    private final OffsetHeader offsetHeader;

    public OffHeapWriteHashServiceImpl(Path directory, GlobType type, HashSet<GlobType> typeToSave,
                                       Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap,
                                       List<HashIndex> index, OffsetHeader offsetHeader) {
        this.directory = directory;
        this.type = type;
        this.typeToSave = typeToSave;
        this.offHeapTypeInfoMap = offHeapTypeInfoMap;
        this.index = index;
        this.offsetHeader = offsetHeader;
    }

    @Override
    public void save(List<Glob> data) throws IOException {
        DataSaver dataSaver = new DataSaver(directory, type, globType -> offHeapTypeInfoMap.get(globType).offHeapTypeInfo,
                new LocalUpdateHeaderAccessor(), new WantedFreeSpace(offsetHeader), currentSize -> currentSize * 2 + 1024);
        final DataSaver.Result result = dataSaver.saveData(data);
        for (HashIndex hashIndex : index) {
            HashWriteIndex hashWriteIndex = new HashWriteIndex(hashIndex);
            hashWriteIndex.save(directory, result.offsets().get(type));
        }
    }

    private class LocalUpdateHeaderAccessor implements DataSaver.UpdateHeaderAccessor {
        @Override
        public DataSaver.UpdateHeader getUpdateHeader(GlobType type, GroupLayout groupLayout) {
            final OffHeapTypeInfoWithFirstLayout offHeapTypeInfoWithFirstLayout = offHeapTypeInfoMap.get(type);
            return new IsFreeUpdateHeader(offHeapTypeInfoWithFirstLayout.freeIdHandle);
        }

        private static class IsFreeUpdateHeader implements DataSaver.UpdateHeader {
            private final VarHandle isFreeVarHandle;

            public IsFreeUpdateHeader(VarHandle isFreeVarHandle) {
                this.isFreeVarHandle = isFreeVarHandle;
            }

            @Override
            public void update(MemorySegment memorySegment, long currenOffset, Glob glob) {
                isFreeVarHandle.set(memorySegment, currenOffset, glob == null ? 1L : 0L);
            }

            @Override
            public ByteBuffer getHeaderFile(long nextFree) {
                final ByteBuffer wrap = ByteBuffer.wrap(new byte[HEADER_SIZE]);
                wrap.putLong(nextFree);
                return wrap;
            }
        }
    }

    private static class WantedFreeSpace implements DataSaver.FreeSpace {
        private final OffsetHeader header;

        public WantedFreeSpace(OffsetHeader offsetHeader) {
            header = offsetHeader;
        }

        @Override
        public int freeTypeCountAtEnd(GlobType globType) {
            return 100;
        }

        @Override
        public int freeByteSpaceAtStart(GlobType globType) {
            return header.offsetAtStart(globType);
        }
    }
}
