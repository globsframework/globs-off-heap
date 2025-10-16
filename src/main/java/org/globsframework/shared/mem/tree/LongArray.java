package org.globsframework.shared.mem.tree;

import java.util.Arrays;

public class LongArray {
    private long[] offset;
    private int size;

    public LongArray(long[] offset) {
        this.offset = offset;
        this.size = offset.length;
    }

    public int size() {
        if (size == -1) {
            throw new IllegalStateException("Already not released.");
        }
        return size;
    }

    public long[] getOffset() {
        if (size == -1) {
            throw new IllegalStateException("Already not released.");
        }
        return offset;
    }

    public void free() {
        size = -1;
    }

    public void take(int wantedSize) {
        if (this.size != -1) {
            throw new IllegalStateException("Already not released.");
        }
        this.size = wantedSize;
        if (offset.length < wantedSize) {
            offset = new long[wantedSize];
        }
    }

    public void add(long value) {
        if (size == -1) {
            throw new IllegalStateException("Already not released.");
        } else {
            if (size == offset.length) {
                offset = Arrays.copyOf(offset, (int) (offset.length * 1.5));
            }
            offset[size++] = value;
        }
    }
}
