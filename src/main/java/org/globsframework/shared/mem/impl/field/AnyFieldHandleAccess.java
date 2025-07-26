package org.globsframework.shared.mem.impl.field;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.impl.read.ReadContext;
import org.globsframework.shared.mem.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class AnyFieldHandleAccess implements HandleAccess {
    private final VarHandle varHandle;
    private final Field field;

    public AnyFieldHandleAccess(VarHandle varHandle, Field field) {
        this.varHandle = varHandle;
        this.field = field;
    }

    public static HandleAccess create(GroupLayout groupLayout, Field field) {
        return new AnyFieldHandleAccess(groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName())), field);
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        varHandle.set(memorySegment, offset, data.getValue(field));
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        data.setValue(field, varHandle.get(memorySegment, offset));
    }

}
