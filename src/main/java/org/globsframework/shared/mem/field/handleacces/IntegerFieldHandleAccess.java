package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetIntAccessor;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class IntegerFieldHandleAccess implements HandleAccess {
    private final VarHandle varHandle;
    private final IntegerField field;
    private final GlobSetIntAccessor setAccessor;

    public IntegerFieldHandleAccess(VarHandle varHandle, IntegerField field) {
        this.varHandle = varHandle;
        this.field = field;
        setAccessor = field.getGlobType().getSetAccessor(field);
    }

    public static HandleAccess create(GroupLayout groupLayout, IntegerField field) {
        return new IntegerFieldHandleAccess(groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName())), field);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final int value = data.get(field, 0);
        varHandle.set(memorySegment, offset, value);
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        setAccessor.set(data, (int)varHandle.get(memorySegment, offset));
    }

}
