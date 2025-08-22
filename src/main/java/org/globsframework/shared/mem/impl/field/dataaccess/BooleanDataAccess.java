package org.globsframework.shared.mem.impl.field.dataaccess;

import org.globsframework.core.metamodel.fields.BooleanField;
import org.globsframework.core.metamodel.fields.DoubleField;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.FieldValues;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class BooleanDataAccess implements DataAccess {
    private final BooleanField field;
    private final VarHandle varHandle;

    public BooleanDataAccess(BooleanField field, VarHandle varHandle) {
        this.field = field;
        this.varHandle = varHandle;
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public int compare(FieldValues functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress) {
        final boolean i = functionalKey.get(field, false);
        final boolean other = (boolean)varHandle.get(memorySegment, 0L, index);
        return Boolean.compare(i, other);
    }

    public static BooleanDataAccess create(GroupLayout groupLayout, BooleanField field) {
        return new BooleanDataAccess(field, groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName())));
    }
}
