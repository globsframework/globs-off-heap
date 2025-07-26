package org.globsframework.shared.mem.impl.field;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.shared.mem.impl.DefaultOffHeapService;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class StringDataAccess implements DataAccess {
    private final VarHandle varAddrHandle;
    private final VarHandle varLenHandle;

    public StringDataAccess(VarHandle varAddrHandle, VarHandle varLenHandle) {
        this.varAddrHandle = varAddrHandle;
        this.varLenHandle = varLenHandle;
    }

    public static DataAccess create(GroupLayout groupLayout, Field field) {
        return new StringDataAccess(groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapService.SUFFIX_ADDR)),
                groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapService.SUFFIX_LEN)));
    }

    public Object get(MemorySegment memorySegment, long offset, StringAccessorByAddress stringAccessorByAddress) {
        int addr = (int) varAddrHandle.get(memorySegment, 0L, offset);
        int len = (int) varLenHandle.get(memorySegment, 0L, offset);
        return stringAccessorByAddress.get(addr, len);
    }
}
