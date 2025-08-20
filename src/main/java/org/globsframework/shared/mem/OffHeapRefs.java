package org.globsframework.shared.mem;

public record OffHeapRefs(LongArray offset) {
    public static OffHeapRefs NULL = new OffHeapRefs(new LongArray(new long[0]));

    int size() {
        return offset.size();
    }
}
