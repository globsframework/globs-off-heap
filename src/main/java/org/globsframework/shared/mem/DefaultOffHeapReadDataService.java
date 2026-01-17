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
import org.globsframework.shared.mem.tree.impl.RootOffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.read.TypeSegment;

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
    private final FileChannel dataChannel;
    private final MemorySegment memorySegment;
    private final long dataSize;
    private final int count;
    private final RootOffHeapTypeInfo offHeapTypeInfo;
    private final FileChannel stringChannel;
    private final Arena arena;
    private final GlobInstantiator globInstantiator;
    private final MappedByteBuffer stringBytesBuffer;
    private final IntHashMap<String> readStrings = new IntHashMap<>();
    private final Map<GlobType, TypeSegment> perGlobTypeMap = new HashMap<>();
    private byte[] cache = new byte[1024];
    private final Map<GlobType, OffHeapTypeInfo> inlined = new HashMap<>();
    private OffHeapTypeInfoAccessor offHeapTypeInfoMap;


    public DefaultOffHeapReadDataService(Path directory, Arena arena, GlobType mainDataType, OffHeapTypeInfoAccessor offHeapTypeInfoMap,
                                         Set<GlobType> typesToSave,  GlobInstantiator globInstantiator) {
        this(directory, arena, mainDataType, offHeapTypeInfoMap, typesToSave, globInstantiator, globType -> 0);

    }
    public DefaultOffHeapReadDataService(Path directory, Arena arena, GlobType mainDataType, OffHeapTypeInfoAccessor offHeapTypeInfoMap,
                                         Set<GlobType> typesToSave,  GlobInstantiator globInstantiator, OffsetHeader offsetHeader) {
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

            this.offHeapTypeInfoMap = offHeapTypeInfoMap;
            this.offHeapTypeInfo = this.offHeapTypeInfoMap.get(mainDataType);


            TypeSegment mainSegment = null;
            for (GlobType globType : typesToSave) {
                final RootOffHeapTypeInfo subOffHeapTypeInfo = offHeapTypeInfoMap.get(globType);
                this.inlined.putAll(subOffHeapTypeInfo.inline());
                final Path pathToFile = directory.resolve(DefaultOffHeapTreeService.createContentFileName(globType));
                final TypeSegment typeSegment = loadMemorySegment(arena, FileChannel.MapMode.READ_ONLY,
                        offsetHeader.offsetAtStart(globType), subOffHeapTypeInfo,
                        pathToFile);
                if (typeSegment != null) {
                    if (globType != mainDataType) {
                        perGlobTypeMap.put(globType, typeSegment);
                    }
                    else {
                        mainSegment =  typeSegment;
                    }
                }
            }
            if (mainSegment != null) {
                this.dataChannel = mainSegment.fileChannel();
                this.count = mainSegment.maxElementCount();
                this.dataSize = mainSegment.size();
                this.memorySegment = mainSegment.segment();
            }
            else  {
                throw new RuntimeException("No memory segment found for " + mainDataType);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static TypeSegment loadMemorySegment(Arena arena, FileChannel.MapMode openMode, int offsetForData,
                                                RootOffHeapTypeInfo subOffHeapTypeInfo, Path pathToFile) throws IOException {
        if (Files.exists(pathToFile)) {
            FileChannel fileChannel = FileChannel.open(pathToFile, StandardOpenOption.READ, openMode == FileChannel.MapMode.READ_ONLY ? StandardOpenOption.READ : StandardOpenOption.WRITE);
            final long size = fileChannel.size();
            int count = Math.toIntExact(size / subOffHeapTypeInfo.primary().byteSizeWithPadding());
            final MemorySegment subData = fileChannel.map(openMode, 0, size, arena);
            return new TypeSegment(subData.asSlice(offsetForData), subData.asSlice(offsetForData, subOffHeapTypeInfo.primary().byteSizeWithPadding() * count), fileChannel,
                    subOffHeapTypeInfo, count, size);
        }
        return null;
    }

    @Override
    public int read(long[] offset, DataConsumer consumer) {
        final int size = offset.length;
        final HandleAccess[] handleAccesses = offHeapTypeInfo.primary().handleAccesses;
        final GlobType type = offHeapTypeInfo.primary().type;
        for (int i = 0; i < size; i++) {
            consumer.accept(readGlob(memorySegment, offset[i], this, handleAccesses, type, field -> true));
        }
        return size;
    }

    @Override
    public void warmup(DataConsumer dataConsumer) {
        for (int i = 0; i < 1000; i++) { // to prevent infinite loop
            long offset = (long) (Math.random() * count) * offHeapTypeInfo.primary().byteSizeWithPadding();
            final Glob read = read(offset);
            if (!dataConsumer.accept(read)) {
                return;
            }
        }
    }

    @Override
    public void readAll(DataConsumer consumer) throws IOException {
        readAll(consumer, this);
    }

    private void readAll(DataConsumer consumer, ReadContext readContext) {
        final long groupSize = offHeapTypeInfo.primary().groupLayout.byteSize();
        final GlobType type = offHeapTypeInfo.primary().type;
        long offset = 0;
        final HandleAccess[] handleAccesses = offHeapTypeInfo.primary().handleAccesses;
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
        final long groupSize = offHeapTypeInfo.primary().groupLayout.byteSize();
        final GlobType type = offHeapTypeInfo.primary().type;
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
        for (HandleAccess handleAccess : offHeapTypeInfo.primary().handleAccesses) {
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
        return readGlob(memorySegment, offset, this, offHeapTypeInfo.primary().handleAccesses,
                offHeapTypeInfo.primary().type, field -> true);
    }

    @Override
    public Glob read(long offset, Predicate<Field> onlyFields) {
        if (offset == -1) {
            return null;
        }
        return readGlob(memorySegment, offset, this, offHeapTypeInfo.primary().handleAccesses,
                offHeapTypeInfo.primary().type, onlyFields);
    }

    @Override
    public Glob read(GlobType targetType, long dataOffset) {
        final TypeSegment typeSegment = perGlobTypeMap.get(targetType);
        if (typeSegment == null) {
            throw new RuntimeException("Bug " + targetType.getName() + " not found");
        }
        final MutableGlob instantiate = globInstantiator.newGlob(targetType);
        for (HandleAccess handleAccess : typeSegment.offHeapTypeInfo().primary().handleAccesses) {
            handleAccess.readAtOffset(instantiate, typeSegment.segment(), dataOffset, this);
        }
        return instantiate;
    }

    synchronized public String get(int addr, int len) {
        String s = readStrings.get(addr);
        if (s == null) {
            if (cache.length < len) {
                cache = new byte[len];
            }
            stringBytesBuffer.position(addr - 4);
            final int writeLen = stringBytesBuffer.getInt();
            if (writeLen != len) {
                throw new RuntimeException("Bug : wrong length between wanted: " + len + " and real: " + writeLen);
            }
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
    public OffHeapTypeInfo getOffHeapInlineTypeInfo(GlobType targetType) {
        return inlined.get(targetType);
    }

    public void close() {
        try {
            if (stringChannel != null) {
                stringChannel.close();
            }
        } catch (IOException x) {
        }
        try {
            dataChannel.close();
        } catch (IOException x) {
        }
    }

    @Override
    public TypeSegment getSegment(GlobType globType) {
        return perGlobTypeMap.get(globType);
    }

    private static class SameGlobInstantiator implements GlobInstantiator {
        private final Map<GlobType, MutableGlob> globs = new HashMap<>();
        @Override
        public MutableGlob newGlob(GlobType globType) {
            return globs.computeIfAbsent(globType, GlobType::instantiate);
        }
    }
}
