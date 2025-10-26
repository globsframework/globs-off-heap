package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.DataSaver;
import org.globsframework.shared.mem.DefaultOffHeapReadDataService;
import org.globsframework.shared.mem.OffsetHeader;
import org.globsframework.shared.mem.hash.OffHeapUpdaterService;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.read.TypeSegment;
import org.globsframework.shared.mem.tree.impl.write.NextFreeOffset;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;
import org.globsframework.shared.mem.tree.impl.write.StringRefType;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

class OffHeapUpdaterServiceImpl implements OffHeapUpdaterService {
    private final Path directory;
    private final Arena arena;
    private final List<OffHeapHashServiceImpl.HashIndex> index;
    private final GlobType type;
    private final HashSet<GlobType> typeToSave;
    private final Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap;
    private final Map<GlobType, FreePosition> freePositions = new HashMap<>();
    private final OffsetHeader offsetHeader;
    private final FileChannel stringChannel;
    private final MappedByteBuffer stringBytesBuffer;
    private final Map<String, Glob> strOffsets = new HashMap<>();
    private int lastFreeStringOffset;

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
                final TypeSegment typeSegment = DefaultOffHeapReadDataService.loadMemorySegment(directory, arena,
                        globType -> offHeapTypeInfoMap.get(globType).offHeapTypeInfo,
                        dataType, FileChannel.MapMode.READ_WRITE, offsetHeader.offsetAtStart(dataType));
                if (typeSegment != null) {
                    freePositions.put(type, new FreePosition(globTypeOffHeapTypeInfoWithFirstLayoutEntry.getValue(), typeSegment));
                }
            }
            final Path resolve = directory.resolve(DefaultOffHeapTreeService.STRINGS_DATA);
            if (Files.exists(resolve)) {
                this.stringChannel = FileChannel.open(resolve, StandardOpenOption.READ);
                this.stringBytesBuffer = stringChannel.map(FileChannel.MapMode.READ_WRITE, 0, stringChannel.size());
                stringBytesBuffer.position(0);
                int len;
                byte[] cache = new byte[1024];
                while (true) {
                    len = stringBytesBuffer.getInt();
                    if (len == -1) {
                        lastFreeStringOffset = stringBytesBuffer.position() -4;
                        break;
                    }
                    if (cache.length < len) {
                        cache = new byte[len + 16];
                    }
                    int position = stringBytesBuffer.position();
                    stringBytesBuffer.get(cache, 0, len);
                    strOffsets.put(new String(cache, 0, len, StandardCharsets.UTF_8),
                            StringRefType.create(position, len));
                }
            }
            else{
                stringChannel = null;
                stringBytesBuffer = null;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class FreePosition {

        private final OffHeapTypeInfoWithFirstLayout offHeapTypeInfo;
        private final TypeSegment typeSegment;

        public FreePosition(OffHeapTypeInfoWithFirstLayout offHeapTypeInfo, TypeSegment typeSegment) {
            this.offHeapTypeInfo = offHeapTypeInfo;
            this.typeSegment = typeSegment;
        }

        public Long nexFree() {
            for (long i = 0; i < typeSegment.maxElementCount(); i++) {
                boolean isFree = (boolean) offHeapTypeInfo.isFreeHandle.get(typeSegment.segment(), 0L, i);
                if (isFree) {
                    return i;
                }
            }
            throw new RuntimeException("No free space for " + offHeapTypeInfo.offHeapTypeInfo.primary().type.getName());
        }
    }

    @Override
    public int update(Glob data) {
        final Map<String, Glob> strings = new HashMap<>();
        DataSaver.getStringGlobMap(List.of(data), strings);
        final Map<String, Integer> offsets = new HashMap<>();

        List<String> toSave = new ArrayList<>();
        for (Map.Entry<String, Glob> stringGlobEntry : strings.entrySet()) {
            final Glob existingOffset = strOffsets.get(stringGlobEntry.getKey());
            if (existingOffset == null) {
                toSave.add(stringGlobEntry.getKey());
            }
        }
        for (String s : toSave) {
            int currentOffset = lastFreeStringOffset;
            stringBytesBuffer.position(lastFreeStringOffset);
            final byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            stringBytesBuffer.putInt(bytes.length);
            stringBytesBuffer.put(bytes);
            lastFreeStringOffset += bytes.length + 4;
            strOffsets.put(s, StringRefType.create(currentOffset + 4, bytes.length));
        }

        final Map<GlobType, IdentityHashMap<Glob, Glob>> extracted = DataSaver.extractGlobToSave(List.of(data));

        final Map<GlobType, IdentityHashMap<Glob, Long>> dataOffsets = new HashMap<>();

        for (Map.Entry<GlobType, IdentityHashMap<Glob, Glob>> globTypeIdentityHashMapEntry : extracted.entrySet()) {
            final IdentityHashMap<Glob, Long> target = new IdentityHashMap<>();
            dataOffsets.put(globTypeIdentityHashMapEntry.getKey(), target);
            for (Glob glob : globTypeIdentityHashMapEntry.getValue().keySet()) {
                target.put(glob, freePositions.get(globTypeIdentityHashMapEntry.getKey()).nexFree());
            }
        }

        for (Map.Entry<GlobType, IdentityHashMap<Glob, Glob>> globTypeIdentityHashMapEntry : extracted.entrySet()) {
            final GlobType typeToSave = globTypeIdentityHashMapEntry.getKey();
            final IdentityHashMap<Glob, Glob> dataToSave = globTypeIdentityHashMapEntry.getValue();
            final OffHeapTypeInfoWithFirstLayout offHeapTypeInfoWithFirstLayout = offHeapTypeInfoMap.get(typeToSave);
            SaveContext saveContext =
                    new SaveContext(dataOffsets, offHeapTypeInfoWithFirstLayout.offHeapTypeInfo.inline()::get, strOffsets::get,
                            new NextFreeOffset(), () -> {

                    });

        }



        // extract all glob to be saved
        // find an index for them in there respective file.
        // write each Glob
        // update hash to point to the new index
        // mark free position ?
        return 0;
    }
}
