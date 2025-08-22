package org.globsframework.shared.mem.impl.field.dataaccess;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.FieldValues;
import org.globsframework.core.utils.Utils;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class AnyDataAccess implements DataAccess {
    private final Field field;
    private final VarHandle varHandle;

    public AnyDataAccess(Field field, VarHandle varHandle) {
        this.field = field;
        this.varHandle = varHandle;
    }

    private Object get(MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress) {
        return varHandle.get(memorySegment, 0L, index);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public int compare(FieldValues functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress) {
        return Utils.compare((Comparable<Object>)functionalKey.getValue(field), get(memorySegment, index, stringAccessorByAddress));
    }

    public static AnyDataAccess create(GroupLayout groupLayout, Field field) {
        return new AnyDataAccess(field, groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName())));
    }
}
