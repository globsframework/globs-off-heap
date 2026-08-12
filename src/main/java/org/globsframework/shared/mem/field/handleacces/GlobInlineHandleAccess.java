package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetGlobAccessor;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class GlobInlineHandleAccess implements HandleAccess {
    private final long isSetOffset;
    private final long dataOffset;
    private final GlobField<?> field;
    private final GlobType targetType;
    private final GlobSetGlobAccessor setAccessor;

    public GlobInlineHandleAccess(long isSetOffset, long dataOffset, GlobField<?> field) {
        this.isSetOffset = isSetOffset;
        this.dataOffset = dataOffset;
        this.field = field;
        targetType = field.getTargetType();
        setAccessor = field.getGlobType().getSetAccessor(field);
    }

    public static HandleAccess create(GroupLayout groupLayout, GlobField<?> globField) {
        final long isSetOffset =
                groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(globField.getName() + DefaultOffHeapTreeService.GLOB_SET));
        final long dataOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(globField.getName()));
        return new GlobInlineHandleAccess(isSetOffset, dataOffset, globField);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        if (!data.isSet(field)) {
            memorySegment.set(ValueLayout.JAVA_BYTE, offset + isSetOffset, (byte) 2);
        } else {
            Glob glob = data.get(field);
            if (glob == null) {
                memorySegment.set(ValueLayout.JAVA_BYTE, offset + isSetOffset, (byte) 1);
            } else {
                memorySegment.set(ValueLayout.JAVA_BYTE, offset + isSetOffset, (byte) 0);
                final MemorySegment slice = memorySegment.asSlice(offset + dataOffset);
                final OffHeapTypeInfo offHeapTypeInfo = saveContext.offHeapTypeInfoMapForInline().get(targetType);
                for (HandleAccess handleAccess : offHeapTypeInfo.handleAccesses) {
                    handleAccess.save(glob, slice, 0, saveContext);
                }
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        byte isSetFlag = memorySegment.get(ValueLayout.JAVA_BYTE, offset + isSetOffset);
        if (isSetFlag == 2) {
            return;
        }
        if (isSetFlag == 1) {
            setAccessor.set(data, null);
            return;
        }
        final MutableGlob instantiate = readContext.newGlob(targetType);
        final MemorySegment slice = memorySegment.asSlice(offset + dataOffset);
        readContext.getOffHeapInlineTypeInfo(targetType).reader.read(instantiate, slice, 0, readContext);
        setAccessor.set(data, instantiate);
    }
}
