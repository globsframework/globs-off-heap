package org.globsframework.shared.mem.tree;

public record OffHeapRef(long offset) {
    public static OffHeapRef NULL = new OffHeapRef(-1);
}
