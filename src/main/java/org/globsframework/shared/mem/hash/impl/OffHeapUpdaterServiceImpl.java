package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.DefaultOffHeapReadDataService;
import org.globsframework.shared.mem.OffsetHeader;
import org.globsframework.shared.mem.hash.OffHeapUpdaterService;
import org.globsframework.shared.mem.tree.impl.read.TypeSegment;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

class OffHeapUpdaterServiceImpl implements OffHeapUpdaterService {
    private final Path directory;
    private final Arena arena;
    private final List<OffHeapHashServiceImpl.HashIndex> index;
    private final GlobType type;
    private final HashSet<GlobType> typeToSave;
    private final Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap;
    private final Map<GlobType, FreePosition> freePositions = new HashMap<>();
    private final OffsetHeader offsetHeader;

    public OffHeapUpdaterServiceImpl(Path directory, Arena arena, List<OffHeapHashServiceImpl.HashIndex> index, GlobType type,
                                     HashSet<GlobType> typeToSave, Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap,
                                     OffsetHeader offsetHeader) {
        this.offsetHeader = offsetHeader;

        try {
            this.directory = directory;
            this.arena = arena;
            this.index = index;
            this.type = type;
            this.typeToSave = typeToSave;
            this.offHeapTypeInfoMap = offHeapTypeInfoMap;
            for (Map.Entry<GlobType, OffHeapTypeInfoWithFirstLayout> globTypeOffHeapTypeInfoWithFirstLayoutEntry : offHeapTypeInfoMap.entrySet()) {
                final GlobType dataType = globTypeOffHeapTypeInfoWithFirstLayoutEntry.getKey();
                final TypeSegment typeSegment = DefaultOffHeapReadDataService.loadMemorySegment(directory, arena, globType -> offHeapTypeInfoMap.get(globType).offHeapTypeInfo,
                        dataType, FileChannel.MapMode.READ_WRITE, offsetHeader.offsetAtStart(dataType));
                if (typeSegment != null) {
                    freePositions.put(type, new FreePosition(globTypeOffHeapTypeInfoWithFirstLayoutEntry.getValue(), typeSegment));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class FreePosition {
        int freeIndex;

        public FreePosition(OffHeapTypeInfoWithFirstLayout offHeapTypeInfo, TypeSegment typeSegment) {
            for (long i = 0; i < typeSegment.maxElementCount(); i++) {
                boolean isFree = (boolean) offHeapTypeInfo.isFreeHandle.get(typeSegment.segment(), 0L, i);
                if (isFree) {
                    freeIndex = Math.toIntExact(i);
                    break;
                }
            }
        }
    }

    @Override
    public int update(Glob data) {


        // extract all glob to be saved
        // find an index for them in there respective file.
        // write each Glob
        // update hash to point to the new index
        // mark free position ?
        return 0;
    }
}
