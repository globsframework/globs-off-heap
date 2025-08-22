package org.globsframework.shared.mem.impl.field.dataaccess;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.model.FieldValues;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class LongDataAccess implements DataAccess {
    private final LongField field;
    private final VarHandle varHandle;

    public LongDataAccess(LongField field, VarHandle varHandle) {
        this.field = field;
        this.varHandle = varHandle;
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public int compare(FieldValues functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress) {
        final long i = functionalKey.get(field, 0);
        final long other = (long) varHandle.get(memorySegment, 0L, index);
        return Long.compare(i, other);
    }

    public static LongDataAccess create(GroupLayout groupLayout, LongField field) {
        return new LongDataAccess(field, groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName())));
    }
}
