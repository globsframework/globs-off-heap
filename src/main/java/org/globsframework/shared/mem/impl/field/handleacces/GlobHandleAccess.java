package org.globsframework.shared.mem.impl.field.handleacces;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.impl.read.ReadContext;
import org.globsframework.shared.mem.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class GlobHandleAccess implements HandleAccess {
    private final VarHandle offsetVarHandle;
    private final GlobField field;
    private final GlobType targetType;

    public GlobHandleAccess(VarHandle offsetVarHandle, GlobField field) {
        this.offsetVarHandle = offsetVarHandle;
        this.field = field;
        targetType = field.getTargetType();
    }

    public static HandleAccess create(GroupLayout groupLayout, GlobField globField) {
        final VarHandle dataVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(globField.getName()));
        return new GlobHandleAccess(dataVarHandle, globField);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        if (!data.isSet(field)) {
            offsetVarHandle.set(memorySegment, offset, -2L);
            return;
        }
        Glob glob = data.get(field);
        if (glob == null) {
            offsetVarHandle.set(memorySegment, offset, -1L);
        } else {
            offsetVarHandle.set(memorySegment, offset, saveContext.getOffset(targetType, glob));
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        long dataOffset = (long) offsetVarHandle.get(memorySegment, offset);
        if (dataOffset == -2) {
            return;
        }
        if (dataOffset == -1) {
            data.set(field, null);
            return;
        }
        Glob d = readContext.read(targetType, dataOffset);
        data.set(field, d);
    }
}
