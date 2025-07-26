package org.globsframework.shared.mem.impl.read;

import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.collections.IntHashMap;
import org.globsframework.shared.mem.*;
import org.globsframework.shared.mem.impl.*;
import org.globsframework.shared.mem.impl.field.HandleAccess;

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

    @Override
    public ReadOffHeapUniqueIndex getIndex(OffHeapUniqueIndex index) {
        return (ReadOffHeapUniqueIndex) indexMap.get(index.getName());
    }

    public ReadOffHeapMultiIndex getIndex(OffHeapNotUniqueIndex index) {
        return (ReadOffHeapMultiIndex) indexMap.get(index.getName());
    }

    @Override
    public void read(OffHeapRefs offHeapRef, DataConsumer consumer) {
        final CurrentOffset currentOffset = new CurrentOffset(0);
        ReadContext readContext = new ReadContext(this::get, currentOffset);
        for (long offset : offHeapRef.offset()) {
            readContext.currentOffset().offset = offset;
            consumer.accept(readGlob(memorySegment, readContext));
        }
    }

    @Override
    public void readAll(DataConsumer consumer) throws IOException {
        final ReadContext readContext = new ReadContext(this::get, new CurrentOffset(0));
        while (true) {
            if (readContext.offset() + offHeapTypeInfo.groupLayout.byteSize() > dataSize) {
                return;
            }
            final MutableGlob instantiate = readGlob(memorySegment, readContext);
            consumer.accept(instantiate);
        }
    }

    private MutableGlob readGlob(MemorySegment memorySegment, ReadContext readContext) {
        final MutableGlob instantiate = offHeapTypeInfo.type.instantiate();
        final long offset = readContext.currentOffset().offset;
        readContext.currentOffset().offset += offHeapTypeInfo.byteSizeWithPadding();
        for (HandleAccess handleAccess : offHeapTypeInfo.handleAccesses) {
            handleAccess.readAtOffset(instantiate, memorySegment, offset, readContext);
        }
        return instantiate;
    }

    @Override
    public Optional<Glob> read(OffHeapRef offHeapRef) {
        ReadContext readContext = new ReadContext(this::get,
                new CurrentOffset(offHeapRef.index()));
        final MutableGlob instantiate =
                readGlob(memorySegment, readContext);
        return Optional.of(instantiate);
    }

    @Override
    synchronized public String get(int addr, int len) {
        final String s = readStrings.get(addr);
        if (s == null) {
            if (cache.length < len) {
                cache = new byte[len];
            }
            stringBytesBuffer.position(addr);
            stringBytesBuffer.get(cache, 0, len);
            readStrings.put(addr, new String(cache, 0, len, StandardCharsets.UTF_8));
        }
        return s;
    }

}
