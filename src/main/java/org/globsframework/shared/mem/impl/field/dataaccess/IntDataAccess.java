package org.globsframework.shared.mem.impl.field.dataaccess;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.model.FieldValues;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class IntDataAccess implements DataAccess {
    private final IntegerField field;
    private final VarHandle varHandle;

    public IntDataAccess(IntegerField field, VarHandle varHandle) {
        this.field = field;
        this.varHandle = varHandle;
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public int compare(FieldValues functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress) {
        final int i = functionalKey.get(field, 0);
        final int other = (int)varHandle.get(memorySegment, 0L, index);
        return Integer.compare(i, other);
    }

    public static IntDataAccess create(GroupLayout groupLayout, IntegerField field) {
        return new IntDataAccess(field, groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName())));
    }
}
