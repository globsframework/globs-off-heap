package org.globsframework.shared.mem.impl.field;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class AnyDataAccess implements DataAccess {
    private final VarHandle varHandle;

    public AnyDataAccess(VarHandle varHandle) {
        this.varHandle = varHandle;
    }

    @Override
    public Object get(MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress) {
        return varHandle.get(memorySegment, 0L, index);
    }

    public static AnyDataAccess create(GroupLayout groupLayout, Field field) {
        return new AnyDataAccess(groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName())));
    }
}
