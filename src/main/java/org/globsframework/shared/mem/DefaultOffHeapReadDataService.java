package org.globsframework.shared.mem;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.GlobInstantiator;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.collections.IntHashMap;
import org.globsframework.shared.mem.field.handleacces.HandleAccess;
import org.globsframework.shared.mem.tree.*;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.read.SegmentPerGlobType;

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
import java.util.function.Predicate;

public class DefaultOffHeapReadDataService implements OffHeapReadDataService, ReadContext {
    private final int count;
    private final OffHeapTypeInfo offHeapTypeInfo;
    private final FileChannel dataChannel;
    private final FileChannel stringChannel;
    private final Arena arena;
    private final GlobInstantiator globInstantiator;
    private final MappedByteBuffer stringBytesBuffer;
    private final IntHashMap<String> readStrings = new IntHashMap<>();
    private final MemorySegment memorySegment;
    private final long dataSize;
    private final Map<GlobType, SegmentPerGlobType> perGlobTypeMap = new HashMap<>();
    private byte[] cache = new byte[1024];

    public DefaultOffHeapReadDataService(Path directory, Arena arena, GlobType mainDataType, Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap,
                                         Set<GlobType> typesToSave,  GlobInstantiator globInstantiator) {
        try {
            this.arena = arena;
            this.globInstantiator = globInstantiator;
            final Path resolve = directory.resolve(DefaultOffHeapTreeService.STRINGS_DATA);
            if (Files.exists(resolve)) {
                this.stringChannel = FileChannel.open(resolve, StandardOpenOption.READ);
                this.stringBytesBuffer = stringChannel.map(FileChannel.MapMode.READ_ONLY, 0, stringChannel.size());
            }
            else {
                this.stringChannel = null;
                this.stringBytesBuffer = null;
            }

            this.offHeapTypeInfo = offHeapTypeInfoMap.get(mainDataType);
            this.dataChannel = FileChannel.open(directory.resolve(DefaultOffHeapTreeService.createContentFileName(mainDataType)), StandardOpenOption.READ);
            this.count = Math.toIntExact(dataChannel.size() / offHeapTypeInfo.byteSizeWithPadding());
            memorySegment = dataChannel.map(FileChannel.MapMode.READ_ONLY,
                    0,
                    count * offHeapTypeInfo.byteSizeWithPadding(), arena);
            dataSize = dataChannel.size();

            for (GlobType globType : typesToSave) {
                if (globType != mainDataType) {
                    loadMemorySegment(directory, arena, offHeapTypeInfoMap, globType);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadMemorySegment(Path directory, Arena arena, Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap, GlobType globType) throws IOException {
        final Path pathToFile = directory.resolve(DefaultOffHeapTreeService.createContentFileName(globType));
        if (Files.exists(pathToFile)) {
            FileChannel fileChannel = FileChannel.open(pathToFile, StandardOpenOption.READ);
            final OffHeapTypeInfo subOffHeapTypeInfo = offHeapTypeInfoMap.get(globType);
            final long size = fileChannel.size();
            int count = Math.toIntExact(size / subOffHeapTypeInfo.byteSizeWithPadding());
            if (size > 0) {
                final MemorySegment subData = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
                SegmentPerGlobType segmentPerGlobType =
                        new SegmentPerGlobType(subData.asSlice(0, subOffHeapTypeInfo.byteSizeWithPadding() * count), fileChannel,
                                subOffHeapTypeInfo);
                perGlobTypeMap.put(globType, segmentPerGlobType);
            }
        }
    }

    @Override
    public int read(long[] offset, DataConsumer consumer) {
        final int size = offset.length;
        final HandleAccess[] handleAccesses = offHeapTypeInfo.handleAccesses;
        final GlobType type = offHeapTypeInfo.type;
        for (int i = 0; i < size; i++) {
            consumer.accept(readGlob(memorySegment, offset[i], this, handleAccesses, type, field -> true));
        }
        return size;
    }

    @Override
    public void warmup() {
        readAll(glob -> {}, this);
    }

    @Override
    public void readAll(DataConsumer consumer) throws IOException {
        readAll(consumer, this);
    }

    private void readAll(DataConsumer consumer, ReadContext readContext) {
        final long groupSize = offHeapTypeInfo.groupLayout.byteSize();
        final GlobType type = offHeapTypeInfo.type;
        long offset = 0;
        final HandleAccess[] handleAccesses = offHeapTypeInfo.handleAccesses;
        while (true) {
            if (offset + groupSize > dataSize) {
                return;
            }
            final MutableGlob instantiate = readGlob(memorySegment, offset, readContext, handleAccesses, type, field -> true);
            consumer.accept(instantiate);
            offset += groupSize;
        }
    }

    @Override
    public void readAll(DataConsumer consumer, Predicate<Field> onlyFields) throws IOException {
        final long groupSize = offHeapTypeInfo.groupLayout.byteSize();
        final GlobType type = offHeapTypeInfo.type;
        final HandleAccess[] accesses = getHandleAccesses(onlyFields);
        long offset = 0;
        while (true) {
            if (offset + groupSize > dataSize) {
                return;
            }
            final MutableGlob instantiate = readGlob(memorySegment, offset, this, accesses, type,  field -> true);
            consumer.accept(instantiate);
            offset += groupSize;
        }
    }

    private HandleAccess[] getHandleAccesses(Predicate<Field> onlyFields) {
        final List<HandleAccess> accesses = new ArrayList<>();
        for (HandleAccess handleAccess : offHeapTypeInfo.handleAccesses) {
            if (onlyFields.test(handleAccess.getField())) {
                accesses.add(handleAccess);
            }
        }
        return accesses.toArray(HandleAccess[]::new);
    }

    private MutableGlob readGlob(MemorySegment memorySegment, long offset, ReadContext readContext,
                                 HandleAccess[] handleAccesses, GlobType type, Predicate<Field> onlyFields) {
        final MutableGlob instantiate = globInstantiator.newGlob(type);
        for (HandleAccess handleAccess : handleAccesses) {
            if (onlyFields.test(handleAccess.getField())) {
                handleAccess.readAtOffset(instantiate, memorySegment, offset, readContext);
            }
        }
        return instantiate;
    }

    @Override
    public Glob read(long offset) {
        if (offset == -1) {
            return null;
        }
        return readGlob(memorySegment, offset, this, offHeapTypeInfo.handleAccesses, offHeapTypeInfo.type, field -> true);
    }

    @Override
    public Glob read(long offset, Predicate<Field> onlyFields) {
        if (offset == -1) {
            return null;
        }
        return readGlob(memorySegment, offset, this, offHeapTypeInfo.handleAccesses, offHeapTypeInfo.type, onlyFields);
    }

    @Override
    public Glob read(GlobType targetType, long dataOffset) {
        final SegmentPerGlobType segmentPerGlobType = perGlobTypeMap.get(targetType);
        if (segmentPerGlobType == null) {
            throw new RuntimeException("Bug " + targetType.getName() + " not found");
        }
        final MutableGlob instantiate = globInstantiator.newGlob(targetType);
        for (HandleAccess handleAccess : segmentPerGlobType.offHeapTypeInfo().handleAccesses) {
            handleAccess.readAtOffset(instantiate, segmentPerGlobType.segment(), dataOffset, this);
        }
        return instantiate;
    }

    synchronized public String get(int addr, int len) {
        String s = readStrings.get(addr);
        if (s == null) {
            if (cache.length < len) {
                cache = new byte[len];
            }
            stringBytesBuffer.position(addr);
            stringBytesBuffer.get(cache, 0, len);
            s = new String(cache, 0, len, StandardCharsets.UTF_8);
            readStrings.put(addr, s);
        }
        return s;
    }

    @Override
    public MutableGlob newGlob(GlobType targetType) {
        return globInstantiator.newGlob(targetType);
    }

    @Override
    public OffHeapTypeInfo getOffHeapTypeInfo(GlobType targetType) {
        return perGlobTypeMap.get(targetType).offHeapTypeInfo();
    }

    public void close() {
        try {
            if (stringChannel != null) {
                stringChannel.close();
            }
        } catch (IOException _) {
        }
        try {
            dataChannel.close();
        } catch (IOException _) {
        }
    }

    private static class SameGlobInstantiator implements GlobInstantiator {
        private final Map<GlobType, MutableGlob> globs = new HashMap<>();
        @Override
        public MutableGlob newGlob(GlobType globType) {
            return globs.computeIfAbsent(globType, GlobType::instantiate);
        }
    }
}
