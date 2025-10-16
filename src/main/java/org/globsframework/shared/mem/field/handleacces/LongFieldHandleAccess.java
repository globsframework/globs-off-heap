package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class LongFieldHandleAccess implements HandleAccess {
    private final VarHandle varHandle;
    private final LongField field;

    public LongFieldHandleAccess(VarHandle varHandle, LongField field) {
        this.varHandle = varHandle;
        this.field = field;
    }

    public static HandleAccess create(GroupLayout groupLayout, LongField field) {
        return new LongFieldHandleAccess(groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName())), field);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final long value = data.get(field, 0);
        varHandle.set(memorySegment, offset, value);
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        data.set(field, (long) varHandle.get(memorySegment, offset));
    }

}
