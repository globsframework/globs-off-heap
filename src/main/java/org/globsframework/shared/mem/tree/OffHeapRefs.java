package org.globsframework.shared.mem.tree;

public record OffHeapRefs(LongArray offset) {
    public static OffHeapRefs NULL = new OffHeapRefs(new LongArray(new long[0]));

    public int size() {
        return offset.size();
    }
}
