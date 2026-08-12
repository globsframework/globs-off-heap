package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetGlobArrayAccessor;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;


/*
  Do not support null glob in the array.
 */
public final class GlobArrayInlineHandleAccess implements HandleAccess {
    private final long lenOffset;
    private final long dataOffset;
    private final GlobArrayField<?> field;
    private final GlobType targetType;
    private final GlobSetGlobArrayAccessor setAccessor;

    public GlobArrayInlineHandleAccess(long lenOffset, long dataOffset, GlobArrayField<?> field) {
        this.lenOffset = lenOffset;
        this.dataOffset = dataOffset;
        this.field = field;
        targetType = field.getTargetType();
        setAccessor = field.getGlobType().getSetAccessor(field);
    }

    public static HandleAccess create(GroupLayout groupLayout, GlobArrayField<?> globField) {
        final long lenOffset =
                groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(globField.getName() + DefaultOffHeapTreeService.GLOB_LEN));
        final long dataOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(globField.getName()));
        return new GlobArrayInlineHandleAccess(lenOffset, dataOffset, globField);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        if (!data.isSet(field)) {
            memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, -2);
        } else {
            Glob[] glob = data.get(field);
            if (glob == null) {
                memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, -1);
            } else {
                memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, glob.length);
                final OffHeapTypeInfo offHeapTypeInfo = saveContext.offHeapTypeInfoMapForInline().get(targetType);
                final MemorySegment arraySegment = memorySegment.asSlice(offset + dataOffset);
                final long byteSize = offHeapTypeInfo.byteSize();
                for (int i = 0; i < glob.length; i++) {
                    Glob g = glob[i];
                    final long inArrayOffset = i * byteSize;
                    for (HandleAccess handleAccess : offHeapTypeInfo.handleAccesses) {
                        handleAccess.save(g, arraySegment, inArrayOffset, saveContext);
                    }
                }
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int len = memorySegment.get(ValueLayout.JAVA_INT, offset + lenOffset);
        if (len == -2) {
            return;
        }
        if (len == -1) {
            setAccessor.setValue(data, null);
            return;
        }
        final OffHeapTypeInfo offHeapTypeInfo = readContext.getOffHeapInlineTypeInfo(targetType);
        final HandleAccess[] handleAccesses = offHeapTypeInfo.handleAccesses;
        final long size = offHeapTypeInfo.byteSize();
        Glob[] globs = new Glob[len];
        final MemorySegment arraySegment = memorySegment.asSlice(offset + dataOffset);
        for (int i = 0; i < globs.length; i++) {
            final MutableGlob instantiate = readContext.newGlob(targetType);
            final long inArrayOffset = i * size;
            for (HandleAccess handleAccess : handleAccesses) {
                handleAccess.readAtOffset(instantiate, arraySegment, inArrayOffset, readContext);
            }
            globs[i] = instantiate;
        }
        setAccessor.set(data, globs);
    }
}
