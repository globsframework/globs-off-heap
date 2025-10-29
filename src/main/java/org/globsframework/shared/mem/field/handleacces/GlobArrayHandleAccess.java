package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class GlobArrayHandleAccess implements HandleAccess {
    private final VarHandle lenVarHandle;
    private final VarHandle sequenceVarHandle;
    private final GlobArrayField field;
    private final GlobType targetType;

    public GlobArrayHandleAccess(VarHandle lenVarHandle, VarHandle sequenceVarHandle, GlobArrayField field) {
        this.lenVarHandle = lenVarHandle;
        this.sequenceVarHandle = sequenceVarHandle;
        this.field = field;
        targetType = field.getTargetType();
    }

    public static HandleAccess create(GroupLayout groupLayout, GlobArrayField globField) {
        final VarHandle lenVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(globField.getName() + DefaultOffHeapTreeService.GLOB_LEN));
        final VarHandle sequenceVarHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(globField.getName()), MemoryLayout.PathElement.sequenceElement());
        return new GlobArrayHandleAccess(lenVarHandle, sequenceVarHandle, globField);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        if (!data.isSet(field)) {
            lenVarHandle.set(memorySegment, offset, -2);
            return;
        }
        Glob[] glob = data.get(field);
        if (glob == null) {
            lenVarHandle.set(memorySegment, offset, -1);
        } else {
            lenVarHandle.set(memorySegment, offset, glob.length);
            for (int i = 0; i < glob.length; i++) {
                Glob g = glob[i];
                if (g == null) {
                    sequenceVarHandle.set(memorySegment, offset, i, -1L);
                } else {
                    sequenceVarHandle.set(memorySegment, offset, (long) i, saveContext.getOffset(targetType, g));
                }
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int len = (int) lenVarHandle.get(memorySegment, offset);
        if (len == -2) {
            data.unset(field);
            return;
        }
        if (len == -1) {
            data.set(field, null);
            return;
        }
        Glob[] globs = new Glob[len];
        for (int i = 0; i < globs.length; i++) {
            long off = (long) sequenceVarHandle.get(memorySegment, offset, (long) i);
            if (off >= 0) {
                globs[i] = readContext.read(targetType, off);
            }
        }
        data.set(field, globs);
    }

    @Override
    public void scanOffset(MemorySegment memorySegment, long offset, ReferenceOffset referenceOffset) {
        int len = (int) lenVarHandle.get(memorySegment, offset);
        if (len == -2) {
            return;
        }
        if (len == -1) {
            return;
        }
        for (int i = 0; i < len; i++) {
            long dataOffset = (long) sequenceVarHandle.get(memorySegment, offset, (long) i);
            if (dataOffset >= 0) {
                referenceOffset.onRef(targetType, dataOffset);
            }
        }
    }
}
