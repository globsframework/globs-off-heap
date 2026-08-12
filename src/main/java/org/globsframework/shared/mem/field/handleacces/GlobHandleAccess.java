package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetGlobAccessor;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class GlobHandleAccess implements HandleAccess {
    private final long refOffset;
    private final GlobField<?> field;
    private final GlobType targetType;
    private final GlobSetGlobAccessor setAccessor;

    public GlobHandleAccess(long refOffset, GlobField<?> field) {
        this.refOffset = refOffset;
        this.field = field;
        targetType = field.getTargetType();
        setAccessor = field.getGlobType().getSetAccessor(field);
    }

    public static HandleAccess create(GroupLayout groupLayout, GlobField<?> globField) {
        final long refOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(globField.getName()));
        return new GlobHandleAccess(refOffset, globField);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        if (!data.isSet(field)) {
            memorySegment.set(ValueLayout.JAVA_LONG, offset + refOffset, -2L);
            return;
        }
        Glob glob = data.get(field);
        if (glob == null) {
            memorySegment.set(ValueLayout.JAVA_LONG, offset + refOffset, -1L);
        } else {
            memorySegment.set(ValueLayout.JAVA_LONG, offset + refOffset, saveContext.getOffset(targetType, glob));
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        long dataOffset = memorySegment.get(ValueLayout.JAVA_LONG, offset + refOffset);
        if (dataOffset == -2) {
            return;
        }
        if (dataOffset == -1) {
            setAccessor.set(data, null);
            return;
        }
        Glob d = readContext.read(targetType, dataOffset);
        setAccessor.set(data, d);
    }

    @Override
    public void scanOffset(MemorySegment memorySegment, long offset, ReferenceOffset referenceOffset) {
        long dataOffset = memorySegment.get(ValueLayout.JAVA_LONG, offset + refOffset);
        if (dataOffset != -2 && dataOffset != -1) {
            referenceOffset.onRef(targetType, dataOffset);
        }
    }
}
