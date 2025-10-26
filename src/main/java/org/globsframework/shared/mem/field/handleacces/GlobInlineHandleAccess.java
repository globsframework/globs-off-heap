package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class GlobInlineHandleAccess implements HandleAccess {
    private final VarHandle byteIsSetHandle;
    private final long dataOffset;
    private final GlobField field;
    private final GlobType targetType;

    public GlobInlineHandleAccess(VarHandle byteIsSetHandle, long dataOffset, GlobField field) {
        this.byteIsSetHandle = byteIsSetHandle;
        this.dataOffset = dataOffset;
        this.field = field;
        targetType = field.getTargetType();
    }

    public static HandleAccess create(GroupLayout groupLayout, GlobField globField) {
        final VarHandle byteIsSetHandle =
                groupLayout.varHandle(MemoryLayout.PathElement.groupElement(globField.getName() + DefaultOffHeapTreeService.GLOB_SET));
        final long dataOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(globField.getName()));
        return new GlobInlineHandleAccess(byteIsSetHandle, dataOffset, globField);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        if (!data.isSet(field)) {
            byteIsSetHandle.set(memorySegment, offset, ((byte) 2));
        } else {
            Glob glob = data.get(field);
            if (glob == null) {
                byteIsSetHandle.set(memorySegment, offset, ((byte) 1));
            } else {
                byteIsSetHandle.set(memorySegment, offset, ((byte) 0));
                final OffHeapTypeInfo offHeapTypeInfo = saveContext.offHeapTypeInfoMapForInline().get(targetType);
                for (HandleAccess handleAccess : offHeapTypeInfo.handleAccesses) {
                    handleAccess.save(glob, memorySegment, offset + dataOffset, saveContext);
                }
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        byte isSetFlag = (byte) byteIsSetHandle.get(memorySegment, offset);
        if (isSetFlag == 2) {
            return;
        }
        if (isSetFlag == 1) {
            data.set(field, null);
            return;
        }
        final HandleAccess[] handleAccesses = readContext.getOffHeapInlineTypeInfo(targetType).handleAccesses;
        final MutableGlob instantiate = readContext.newGlob(targetType);
        for (HandleAccess handleAccess : handleAccesses) {
            handleAccess.readAtOffset(instantiate, memorySegment, offset + dataOffset, readContext);
        }
        data.set(field, instantiate);
    }
}
