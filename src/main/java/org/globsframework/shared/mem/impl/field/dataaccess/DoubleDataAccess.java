package org.globsframework.shared.mem.impl.field.dataaccess;

import org.globsframework.core.metamodel.fields.DoubleField;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.model.FieldValues;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class DoubleDataAccess implements DataAccess {
    private final DoubleField field;
    private final VarHandle varHandle;

    public DoubleDataAccess(DoubleField field, VarHandle varHandle) {
        this.field = field;
        this.varHandle = varHandle;
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public int compare(FieldValues functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress) {
        final double i = functionalKey.get(field, 0);
        final double other = (double)varHandle.get(memorySegment, 0L, index);
        return Double.compare(i, other);
    }

    public static DoubleDataAccess create(GroupLayout groupLayout, DoubleField field) {
        return new DoubleDataAccess(field, groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName())));
    }
}
