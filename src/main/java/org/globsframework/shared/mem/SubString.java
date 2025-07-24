package org.globsframework.shared.mem;


class SubString implements CharSequence {
    private final char[] data;
    private final int offset;
    private final int len;

    SubString(char[] data, int offset, int len) {
        this.data = data;
        this.offset = offset;
        this.len = len;
    }

    public int length() {
        return len;
    }

    public char charAt(int index) {
        return data[offset + index];
    }

    public CharSequence subSequence(int start, int end) {
        return new SubString(data, offset + start, end - start);
    }
}
