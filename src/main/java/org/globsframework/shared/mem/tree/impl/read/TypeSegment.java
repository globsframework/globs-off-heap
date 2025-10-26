package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;

import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;

public record TypeSegment(MemorySegment headerSegment, MemorySegment segment, FileChannel fileChannel, OffHeapTypeInfo offHeapTypeInfo, int maxElementCount,
                          long size) {
}
