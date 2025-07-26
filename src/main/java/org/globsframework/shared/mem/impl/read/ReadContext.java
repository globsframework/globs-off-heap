package org.globsframework.shared.mem.impl.read;

import org.globsframework.shared.mem.impl.StringAccessorByAddress;

public record ReadContext(StringAccessorByAddress stringAccessorByAddress, CurrentOffset currentOffset) {
    public long offset() {
        return currentOffset.offset;
    }
}
