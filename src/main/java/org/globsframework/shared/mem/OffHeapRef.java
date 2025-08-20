package org.globsframework.shared.mem;

public record OffHeapRef(long index) {
    public static OffHeapRef NULL = new OffHeapRef(-1);
}
