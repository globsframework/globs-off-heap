package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetLongAccessor;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class LongFieldHandleAccess implements HandleAccess {
    private final long fieldOffset;
    private final LongField field;
    private final GlobSetLongAccessor setAccessor;

    public LongFieldHandleAccess(long fieldOffset, LongField field) {
        this.fieldOffset = fieldOffset;
        this.field = field;
        setAccessor = field.getGlobType().getSetAccessor(field);
    }

    public static HandleAccess create(GroupLayout groupLayout, LongField field) {
        return new LongFieldHandleAccess(groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(field.getName())), field);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final long value = data.get(field, 0);
        memorySegment.set(ValueLayout.JAVA_LONG, offset + fieldOffset, value);
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        setAccessor.set(data, memorySegment.get(ValueLayout.JAVA_LONG, offset + fieldOffset));
    }

}
