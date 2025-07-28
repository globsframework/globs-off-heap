package org.globsframework.shared.mem.impl.field.dataaccess;

import org.globsframework.core.model.FieldValues;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.MemorySegment;

public interface DataAccess {
    Object get(MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress);

    int compare(FieldValues functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress);
}
