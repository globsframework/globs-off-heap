package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.DataSaver;
import org.globsframework.shared.mem.DefaultOffHeapReadDataService;
import org.globsframework.shared.mem.OffsetHeader;
import org.globsframework.shared.mem.field.handleacces.HandleAccess;
import org.globsframework.shared.mem.hash.OffHeapUpdaterService;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.read.TypeSegment;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;
import org.globsframework.shared.mem.tree.impl.write.StringRef;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
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
    private final List<HashIndex> index;
    private final GlobType type;
    private final HashSet<GlobType> typeToSave;
    private final Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap;
    private final Map<GlobType, SegmentPosition> freePositions = new HashMap<>();
    private final OffsetHeader offsetHeader;
    private final FileChannel stringChannel;
    private final MappedByteBuffer stringBytesBuffer;
    private final Map<String, StringRef> strOffsets = new HashMap<>();
    private final DefaultOffHeapReadDataService readDataService;
    private final List<Updater> updaters;
    private int lastFreeStringOffset;
    private long countUpdate = 0;

    public OffHeapUpdaterServiceImpl(Path directory, Arena arena, List<HashIndex> index, GlobType type,
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
                final Path pathToFile = directory.resolve(DefaultOffHeapTreeService.createContentFileName(dataType));
                final TypeSegment typeSegment = DefaultOffHeapReadDataService.loadMemorySegment(arena,
                        FileChannel.MapMode.READ_WRITE, offsetHeader.offsetAtStart(dataType), offHeapTypeInfoMap.get(dataType).offHeapTypeInfo.primary(),
                        pathToFile);
                if (typeSegment != null) {
                    freePositions.put(dataType, new SegmentPosition(globTypeOffHeapTypeInfoWithFirstLayoutEntry.getValue(), typeSegment));
                }
            }
            final Path resolve = directory.resolve(DefaultOffHeapTreeService.STRINGS_DATA);
            if (Files.exists(resolve)) {
                this.stringChannel = FileChannel.open(resolve, StandardOpenOption.READ, StandardOpenOption.WRITE);
                long strFileSize = stringChannel.size();
                this.stringBytesBuffer = stringChannel.map(FileChannel.MapMode.READ_WRITE, 0, stringChannel.size());
                stringBytesBuffer.position(0);
                int len;
                byte[] cache = new byte[1024];
                while (true) {
                    if (stringBytesBuffer.position() == strFileSize) {
                        lastFreeStringOffset = -1;
                        break;
                    }
                    len = stringBytesBuffer.getInt();
                    if (len == 0) {
                        lastFreeStringOffset = stringBytesBuffer.position() - 4;
                        break;
                    }
                    if (cache.length < len) {
                        cache = new byte[len + 16];
                    }
                    int position = stringBytesBuffer.position();
                    stringBytesBuffer.get(cache, 0, len);
                    strOffsets.put(new String(cache, 0, len, StandardCharsets.UTF_8),
                            StringRef.create(position, len));
                }
            } else {
                stringChannel = null;
                stringBytesBuffer = null;
            }

            readDataService =
                    new DefaultOffHeapReadDataService(directory, arena, type,
                            globType -> offHeapTypeInfoMap.get(globType).offHeapTypeInfo,
                            typeToSave, GlobType::instantiate, offsetHeader);

            updaters = new ArrayList<>();
            for (HashIndex hashIndex : index) {
                updaters.add(new Updater(hashIndex, directory, arena));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static class SegmentPosition {
        private final OffHeapTypeInfoWithFirstLayout offHeapTypeInfo;
        private final TypeSegment typeSegment;

        public SegmentPosition(OffHeapTypeInfoWithFirstLayout offHeapTypeInfo, TypeSegment typeSegment) {
            this.offHeapTypeInfo = offHeapTypeInfo;
            this.typeSegment = typeSegment;
        }

        public void setToFree(long dataOffset, long timestamp) {
            offHeapTypeInfo.freeIdHandle.set(typeSegment.segment(), dataOffset, timestamp);
        }

        public Long nexFree(long now) {
            final long size = typeSegment.offHeapTypeInfo().byteSizeWithPadding();
            for (long i = 0; i < typeSegment.maxElementCount(); i++) {
                long timestamp = (long) offHeapTypeInfo.freeIdHandle.get(typeSegment.segment(), i * size);
                if (timestamp != 0 && (now - timestamp) > 1000) {
                    return i * size;
                }
            }
            throw new RuntimeException("No free space for " + offHeapTypeInfo.offHeapTypeInfo.primary().type.getName());
        }
    }

    @Override
    synchronized public int update(Glob data) {
        final Map<String, StringRef> strings = new HashMap<>();
        DataSaver.getStringGlobMap(List.of(data), strings);

        List<String> toSave = new ArrayList<>();
        for (Map.Entry<String, StringRef> stringGlobEntry : strings.entrySet()) {
            final StringRef existingOffset = strOffsets.get(stringGlobEntry.getKey());
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
            strOffsets.put(s, StringRef.create(currentOffset + 4, bytes.length));
        }

        final Map<GlobType, IdentityHashMap<Glob, Glob>> extracted = DataSaver.extractGlobToSave(List.of(data));

        extracted.computeIfAbsent(data.getType(), k -> new IdentityHashMap<>())
                .put(data, data);

        final Map<GlobType, IdentityHashMap<Glob, Long>> dataOffsets = new HashMap<>();

        final long now = System.currentTimeMillis();
        for (Map.Entry<GlobType, IdentityHashMap<Glob, Glob>> globTypeIdentityHashMapEntry : extracted.entrySet()) {
            final IdentityHashMap<Glob, Long> target = new IdentityHashMap<>();
            dataOffsets.put(globTypeIdentityHashMapEntry.getKey(), target);
            for (Glob glob : globTypeIdentityHashMapEntry.getValue().keySet()) {
                target.put(glob, freePositions.get(globTypeIdentityHashMapEntry.getKey()).nexFree(now));
            }
        }

        for (Map.Entry<GlobType, IdentityHashMap<Glob, Glob>> globTypeIdentityHashMapEntry : extracted.entrySet()) {
            final GlobType typeToSave = globTypeIdentityHashMapEntry.getKey();
            final IdentityHashMap<Glob, Glob> dataToSave = globTypeIdentityHashMapEntry.getValue();
            final OffHeapTypeInfoWithFirstLayout offHeapTypeInfoWithFirstLayout = offHeapTypeInfoMap.get(typeToSave);
            SaveContext saveContext =
                    new SaveContext(dataOffsets, offHeapTypeInfoWithFirstLayout.offHeapTypeInfo.inline()::get, strOffsets::get);
            final HandleAccess[] handleAccesses = offHeapTypeInfoWithFirstLayout.offHeapTypeInfo.primary().handleAccesses;
            final TypeSegment typeSegment = freePositions.get(typeToSave).typeSegment;
            final IdentityHashMap<Glob, Long> globLongIdentityHashMap = dataOffsets.get(typeToSave);
            for (Glob glob : dataToSave.keySet()) {
                final Long offset = globLongIdentityHashMap.get(glob);
                if (((long)offHeapTypeInfoWithFirstLayout.freeIdHandle.get(typeSegment.segment(), offset)) == 0){
                    throw new RuntimeException("Bug : place at " + offset + " is not free for " + typeToSave.getName());
                }
                offHeapTypeInfoWithFirstLayout.freeIdHandle.set(typeSegment.segment(), offset, 0);
                for (HandleAccess handleAccess : handleAccesses) {
                    handleAccess.save(glob, typeSegment.segment(), offset, saveContext);
                }
            }
        }
        final long mainDataOffset = dataOffsets.get(data.getType()).get(data);

        long dataIndex = 0;
        long previousDataIndex = -1L;
        for (Updater updater : updaters) {
            dataIndex = updater.save(data, mainDataOffset, readDataService);
            if (previousDataIndex != -1L) {
                if (dataIndex != previousDataIndex) {
                    throw new RuntimeException("Data " + dataIndex + " != " + previousDataIndex);
                }
            }
            previousDataIndex = dataIndex;
        }

        if (previousDataIndex != -1L) {
            extractOffsetToReset(previousDataIndex);
        }
        return 0;
    }

    record ToScan(GlobType globType, long dataOffset) {}

    private void extractOffsetToReset(long previousDataIndex) {
        Map<GlobType, Set<Long>> dataOffsetsToFree = new HashMap<>();
        List<ToScan> indexToScan = new ArrayList<>();
        indexToScan.add(new ToScan(type, previousDataIndex));
        while (!indexToScan.isEmpty()){
            ToScan toScan = indexToScan.removeFirst();
            for (HandleAccess handleAccess : offHeapTypeInfoMap.get(toScan.globType()).offHeapTypeInfo.primary().handleAccesses) {
                handleAccess.scanOffset(freePositions.get(type).typeSegment.segment(), toScan.dataOffset(), new HandleAccess.ReferenceOffset() {
                    @Override
                    public void onRef(GlobType type, long offset) {
                        if (dataOffsetsToFree.computeIfAbsent(type, k -> new HashSet<>())
                                .add(offset)) {
                            indexToScan.add(new ToScan(type, offset));
                        }
                    }
                });
            }
        }

        final long now = System.currentTimeMillis();
        for (Map.Entry<GlobType, Set<Long>> globTypeListEntry : dataOffsetsToFree.entrySet()) {
            final SegmentPosition segmentPosition = freePositions.get(globTypeListEntry.getKey());
            globTypeListEntry.getValue().forEach(offset -> {
                segmentPosition.setToFree(offset, now);
            });
        }
    }
}
