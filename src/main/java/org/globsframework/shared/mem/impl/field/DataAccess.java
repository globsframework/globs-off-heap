package org.globsframework.shared.mem.impl.field;

import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.MemorySegment;

public interface DataAccess {
    Object get(MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress);
}
