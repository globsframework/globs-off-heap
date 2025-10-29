package org.globsframework.shared.mem.tree.impl.write;

public class StringRef {
    public int offset;
    public int len;

    public StringRef() {
    }

    public StringRef(int offset, int len) {
        this.offset = offset;
        this.len = len;
    }

    public static StringRef create(int offset, int len) {
        return new StringRef(offset, len);
    }
}
