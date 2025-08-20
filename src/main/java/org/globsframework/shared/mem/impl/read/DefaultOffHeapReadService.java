package org.globsframework.shared.mem.impl.read;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.collections.IntHashMap;
import org.globsframework.shared.mem.*;
import org.globsframework.shared.mem.impl.*;
import org.globsframework.shared.mem.impl.field.handleacces.HandleAccess;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class DefaultOffHeapReadService implements OffHeapReadService, StringAccessorByAddress {
    private final int count;
    private final OffHeapTypeInfo offHeapTypeInfo;
    private final FileChannel dataChannel;
    private final FileChannel stringChannel;
    private final Arena arena;
    private final MappedByteBuffer stringBytesBuffer;
    private final IntHashMap<String> readStrings = new IntHashMap<>();
    private final Map<String, ReadIndex> indexMap;
    private final MemorySegment memorySegment;
    private final long dataSize;
    private byte[] cache = new byte[1024];

    public DefaultOffHeapReadService(Path directory, Arena arena, OffHeapTypeInfo offHeapTypeInfo, Map<String, Index> index) throws IOException {
        this.arena = arena;
        this.dataChannel = FileChannel.open(directory.resolve(DefaultOffHeapService.CONTENT_DATA), StandardOpenOption.READ);
        this.stringChannel = FileChannel.open(directory.resolve(DefaultOffHeapService.STRINGS_DATA), StandardOpenOption.READ);
        stringBytesBuffer = stringChannel.map(FileChannel.MapMode.READ_ONLY, 0, stringChannel.size());
        this.count = Math.toIntExact(dataChannel.size() / offHeapTypeInfo.byteSizeWithPadding());
        this.offHeapTypeInfo = offHeapTypeInfo;
        indexMap = new HashMap<>();
        for (Map.Entry<String, Index> entry : index.entrySet()) {
            if (entry.getValue().isUnique()) {
                indexMap.put(entry.getKey(), new DefaultReadOffHeapUniqueIndex(directory, (OffHeapUniqueIndex) entry.getValue(), this));
            }else {
                indexMap.put(entry.getKey(), new DefaultReadOffHeapManyIndex(directory, (OffHeapNotUniqueIndex) entry.getValue(), this));
            }

        }
        memorySegment = dataChannel.map(FileChannel.MapMode.READ_ONLY,
                0,
                count * offHeapTypeInfo.byteSizeWithPadding(), arena);
        dataSize = dataChannel.size();
    }

    public ReadOffHeapUniqueIndex getIndex(OffHeapUniqueIndex index) {
        return (ReadOffHeapUniqueIndex) indexMap.get(index.getName());
    }

    public ReadOffHeapMultiIndex getIndex(OffHeapNotUniqueIndex index) {
        return (ReadOffHeapMultiIndex) indexMap.get(index.getName());
    }

    public int read(OffHeapRefs offHeapRef, DataConsumer consumer) {
        ReadContext readContext = new ReadContext(this);
        final long[] offset = offHeapRef.offset().getOffset();
        final int size = offHeapRef.offset().size();
        final HandleAccess[] handleAccesses = offHeapTypeInfo.handleAccesses;
        final GlobType type = offHeapTypeInfo.type;
        for (int i = 0; i < size; i++) {
            consumer.accept(readGlob(memorySegment, offset[i], readContext, handleAccesses, type));
        }
        return size;
    }

    public void readAll(DataConsumer consumer) throws IOException {
        final ReadContext readContext = new ReadContext(this);
        final long groupSize = offHeapTypeInfo.groupLayout.byteSize();
        final GlobType type = offHeapTypeInfo.type;
        long offset = 0;
        final HandleAccess[] handleAccesses = offHeapTypeInfo.handleAccesses;
        while (true) {
            if (offset + groupSize > dataSize) {
                return;
            }
            final MutableGlob instantiate = readGlob(memorySegment, offset, readContext, handleAccesses, type);
            consumer.accept(instantiate);
            offset += groupSize;
        }
    }

    public void readAll(DataConsumer consumer, Set<Field> onlyFields) throws IOException {
        final ReadContext readContext = new ReadContext(this);
        final long groupSize = offHeapTypeInfo.groupLayout.byteSize();
        final GlobType type = offHeapTypeInfo.type;
        final HandleAccess[] accesses = getHandleAccesses(onlyFields);
        long offset = 0;
        while (true) {
            if (offset + groupSize > dataSize) {
                return;
            }
            final MutableGlob instantiate = readGlob(memorySegment, offset, readContext, accesses, type);
            consumer.accept(instantiate);
            offset += groupSize;
        }
    }

    private HandleAccess[] getHandleAccesses(Set<Field> onlyFields) {
        final HandleAccess[] accesses = new HandleAccess[onlyFields.size()];
        int i = 0;
        for (HandleAccess handleAccess : offHeapTypeInfo.handleAccesses) {
            if (onlyFields.contains(handleAccess.getField())) {
                accesses[i] = handleAccess;
                i++;
            }
        }
        return accesses;
    }

    private MutableGlob readGlob(MemorySegment memorySegment, long offset, ReadContext readContext, HandleAccess[] handleAccesses, GlobType type) {
        final MutableGlob instantiate = type.instantiate();
        for (HandleAccess handleAccess : handleAccesses) {
            handleAccess.readAtOffset(instantiate, memorySegment, offset, readContext);
        }
        return instantiate;
    }

    public Optional<Glob> read(OffHeapRef offHeapRef) {
        if (offHeapRef == null || offHeapRef.index() == -1) {
            return Optional.empty();
        }
        ReadContext readContext = new ReadContext(this);
        final MutableGlob instantiate =
                readGlob(memorySegment, offHeapRef.index(), readContext, offHeapTypeInfo.handleAccesses, offHeapTypeInfo.type);
        return Optional.of(instantiate);
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

    public void close() throws IOException {
        for (ReadIndex value : indexMap.values()) {
            try {
                ((AutoCloseable)value).close();
            } catch (Exception e) {
            }
        }
        try {
            stringChannel.close();
        } catch (IOException e) {
        }
        dataChannel.close();
    }
}
