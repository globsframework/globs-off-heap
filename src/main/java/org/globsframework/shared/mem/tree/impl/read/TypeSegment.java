package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.shared.mem.tree.impl.RootOffHeapTypeInfo;

import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;

public record TypeSegment(MemorySegment headerSegment, MemorySegment segment, FileChannel fileChannel,
                          RootOffHeapTypeInfo offHeapTypeInfo, int maxElementCount, long size) {
}
