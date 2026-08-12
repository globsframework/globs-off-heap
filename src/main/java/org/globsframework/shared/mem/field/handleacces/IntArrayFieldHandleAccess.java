package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.annotations.ArraySize;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.IntegerArrayField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetIntArrayAccessor;
import org.globsframework.core.utils.exceptions.InvalidParameter;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class IntArrayFieldHandleAccess implements HandleAccess {
    private final IntegerArrayField field;
    private final long lenOffset;
    private final long arrayBaseOffset;
    private final Integer size;
    private final GlobSetIntArrayAccessor setAccessor;

    public IntArrayFieldHandleAccess(long lenOffset, long arrayBaseOffset,
                                     int size, IntegerArrayField field) {
        this.size = size;
        this.field = field;
        this.lenOffset = lenOffset;
        this.arrayBaseOffset = arrayBaseOffset;
        setAccessor = field.getGlobType().getSetAccessor(field);
    }

    public static HandleAccess create(GroupLayout groupLayout, IntegerArrayField field) {
        int size = field.getAnnotation(ArraySize.KEY).getNotNull(ArraySize.VALUE);
        final long lenOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapTreeService.STRING_SUFFIX_LEN));
        // the byte offset of the sequence inside the record : what a bulk copy needs
        final long arrayBaseOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(field.getName()));
        return new IntArrayFieldHandleAccess(lenOffset, arrayBaseOffset, size, field);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final int[] value = data.get(field);
        if (value != null && value.length > size) {
            throw new InvalidParameter("Array size " + value.length + " is too large. Max is " + size);
        }
        if (value == null) {
            memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, data.isSet(field) ? -1 : -2);
        }
        else {
            memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, value.length);
            MemorySegment.copy(value, 0, memorySegment, ValueLayout.JAVA_INT, offset + arrayBaseOffset, value.length);
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        final int size = memorySegment.get(ValueLayout.JAVA_INT, offset + lenOffset);
        if (size == -1) {
            setAccessor.set(data, null);

        } else if (size >= 0) {
            int[] value = new int[size];
            // one bulk copy rather than size VarHandle calls
            MemorySegment.copy(memorySegment, ValueLayout.JAVA_INT, offset + arrayBaseOffset, value, 0, size);
            setAccessor.set(data, value);
        }
    }
}
