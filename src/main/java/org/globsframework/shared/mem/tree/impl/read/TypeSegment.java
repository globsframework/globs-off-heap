package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.shared.mem.tree.impl.RootOffHeapTypeInfo;

import java.lang.foreign.MemorySegment;

public record TypeSegment(MemorySegment headerSegment, MemorySegment segment,
                          RootOffHeapTypeInfo offHeapTypeInfo, int maxElementCount, long size) {
}
