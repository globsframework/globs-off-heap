package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;

import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.util.Objects;

public final class SegmentPerGlobType {
    private final MemorySegment segment;
    private final FileChannel fileChannel;
    private final OffHeapTypeInfo offHeapTypeInfo;
    private long freeOffset;

    public SegmentPerGlobType(MemorySegment segment, FileChannel fileChannel, OffHeapTypeInfo offHeapTypeInfo) {
        this.segment = segment;
        this.fileChannel = fileChannel;
        this.offHeapTypeInfo = offHeapTypeInfo;
    }

    public MemorySegment segment() {
        return segment;
    }

    public FileChannel fileChannel() {
        return fileChannel;
    }

    public OffHeapTypeInfo offHeapTypeInfo() {
        return offHeapTypeInfo;
    }

    void findFirstFreeOffset() {

    }
}
