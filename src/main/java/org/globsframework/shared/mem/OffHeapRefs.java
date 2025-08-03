package org.globsframework.shared.mem;

public record OffHeapRefs(LongArray offset) {
    int size() {
        return offset.size();
    }
}
