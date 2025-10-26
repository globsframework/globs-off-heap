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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class OffHeapWriteHashServiceImpl implements OffHeapWriteHashService {
    private final Path directory;
    private final GlobType type;
    private final HashSet<GlobType> typeToSave;
    private final Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap;
    private final List<OffHeapHashServiceImpl.HashIndex> index;
    private final OffsetHeader offsetHeader;

    public OffHeapWriteHashServiceImpl(Path directory, GlobType type, HashSet<GlobType> typeToSave,
                                       Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap, List<OffHeapHashServiceImpl.HashIndex> index, OffsetHeader offsetHeader) {
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
                new LocalUpdateHeaderAccessor(), new WantedFreeSpace(offsetHeader));
        final DataSaver.Result result = dataSaver.saveData(data);
        for (OffHeapHashServiceImpl.HashIndex hashIndex : index) {
            HashWriteIndex hashWriteIndex = new HashWriteIndex(hashIndex.size(), result.offsets().get(type), hashIndex.keyBuilder());
            final Path resolve = directory.resolve(hashIndex.name());
            Files.createDirectories(resolve);
            hashWriteIndex.save(resolve);
        }
    }

    private class LocalUpdateHeaderAccessor implements DataSaver.UpdateHeaderAccessor {
        @Override
        public DataSaver.UpdateHeader getUpdateHeader(GlobType type, GroupLayout groupLayout) {
            final OffHeapTypeInfoWithFirstLayout offHeapTypeInfoWithFirstLayout = offHeapTypeInfoMap.get(type);
            return new IsFreeUpdateHeader(offHeapTypeInfoWithFirstLayout.isFreeHandle);
        }

        private static class IsFreeUpdateHeader implements DataSaver.UpdateHeader {
            private final VarHandle isFreeVarHandle;

            public IsFreeUpdateHeader(VarHandle isFreeVarHandle) {
                this.isFreeVarHandle = isFreeVarHandle;
            }

            @Override
            public void update(MemorySegment memorySegment, long currenOffset, Glob glob) {
                isFreeVarHandle.set(memorySegment, currenOffset, glob == null);
            }
        }
    }

    private static class WantedFreeSpace implements DataSaver.FreeSpace {
        private final OffsetHeader header;

        public WantedFreeSpace(OffsetHeader offsetHeader) {
            header = offsetHeader;
        }

        @Override
        public int freeTypeSizeSpaceAtEnd(GlobType globType) {
            return 100;
        }

        @Override
        public int freeByteSpaceAtStart(GlobType globType) {
            return header.offsetAtStart(globType);
        }
    }
}
