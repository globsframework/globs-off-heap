package org.globsframework.shared.mem.impl.field.handleacces;

import org.globsframework.core.metamodel.fields.DoubleField;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.impl.read.ReadContext;
import org.globsframework.shared.mem.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class DoubleFieldHandleAccess implements HandleAccess {
    private final VarHandle varHandle;
    private final DoubleField field;

    public DoubleFieldHandleAccess(VarHandle varHandle, DoubleField field) {
        this.varHandle = varHandle;
        this.field = field;
    }

    public static HandleAccess create(GroupLayout groupLayout, DoubleField field) {
        return new DoubleFieldHandleAccess(groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName())), field);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final double value = data.get(field, 0);
        varHandle.set(memorySegment, offset, value);
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        data.set(field, (double) varHandle.get(memorySegment, offset));
    }
}
