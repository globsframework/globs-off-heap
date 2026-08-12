package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetGlobArrayAccessor;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class GlobArrayHandleAccess implements HandleAccess {
    private final long lenOffset;
    private final long sequenceOffset;
    private final GlobArrayField<?> field;
    private final GlobType targetType;
    private final GlobSetGlobArrayAccessor setAccessor;

    public GlobArrayHandleAccess(long lenOffset, long sequenceOffset, GlobArrayField<?> field) {
        this.lenOffset = lenOffset;
        this.sequenceOffset = sequenceOffset;
        this.field = field;
        targetType = field.getTargetType();
        setAccessor = field.getGlobType().getSetAccessor(field);
    }

    public static HandleAccess create(GroupLayout groupLayout, GlobArrayField<?> globField) {
        final long lenOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(globField.getName() + DefaultOffHeapTreeService.GLOB_LEN));
        final long sequenceOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(globField.getName()));
        return new GlobArrayHandleAccess(lenOffset, sequenceOffset, globField);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        if (!data.isSet(field)) {
            memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, -2);
            return;
        }
        Glob[] glob = data.get(field);
        if (glob == null) {
            memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, -1);
        } else {
            memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, glob.length);
            final long base = offset + sequenceOffset;
            for (int i = 0; i < glob.length; i++) {
                Glob g = glob[i];
                memorySegment.set(ValueLayout.JAVA_LONG, base + i * 8L,
                        g == null ? -1L : saveContext.getOffset(targetType, g));
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int len = memorySegment.get(ValueLayout.JAVA_INT, offset + lenOffset);
        if (len == -2) {
            data.unset(field);
            return;
        }
        if (len == -1) {
            setAccessor.set(data, null);
            return;
        }
        Glob[] globs = new Glob[len];
        final long base = offset + sequenceOffset;
        for (int i = 0; i < globs.length; i++) {
            long off = memorySegment.get(ValueLayout.JAVA_LONG, base + i * 8L);
            if (off >= 0) {
                globs[i] = readContext.read(targetType, off);
            }
        }
        setAccessor.set(data, globs);
    }

    @Override
    public void scanOffset(MemorySegment memorySegment, long offset, ReferenceOffset referenceOffset) {
        int len = memorySegment.get(ValueLayout.JAVA_INT, offset + lenOffset);
        if (len == -2) {
            return;
        }
        if (len == -1) {
            return;
        }
        final long base = offset + sequenceOffset;
        for (int i = 0; i < len; i++) {
            long dataOffset = memorySegment.get(ValueLayout.JAVA_LONG, base + i * 8L);
            if (dataOffset >= 0) {
                referenceOffset.onRef(targetType, dataOffset);
            }
        }
    }
}
