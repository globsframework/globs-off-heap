package org.globsframework.shared.mem.impl.field.handleacces;

import org.globsframework.core.metamodel.annotations.ArraySize;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.IntegerArrayField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.exceptions.InvalidParameter;
import org.globsframework.shared.mem.impl.DefaultOffHeapService;
import org.globsframework.shared.mem.impl.read.ReadContext;
import org.globsframework.shared.mem.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class IntArrayFieldHandleAccess implements HandleAccess {
    private final IntegerArrayField field;
    private final VarHandle lenHandle;
    private final VarHandle arrayHandle;
    private final Integer size;

    public IntArrayFieldHandleAccess(VarHandle lenHandle, VarHandle arrayHandle, int size, IntegerArrayField field) {
        this.size = size;
        this.field = field;
        this.lenHandle = lenHandle;
        this.arrayHandle = arrayHandle;
    }

    public static HandleAccess create(GroupLayout groupLayout, IntegerArrayField field) {
        int size = field.getAnnotation(ArraySize.KEY).getNotNull(ArraySize.VALUE);
        final VarHandle lenHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapService.STRING_SUFFIX_LEN));
        final VarHandle arrayHandle = groupLayout.varHandle(
                MemoryLayout.PathElement.groupElement(field.getName()),
                MemoryLayout.PathElement.sequenceElement());
        return new IntArrayFieldHandleAccess(lenHandle, arrayHandle, size, field);
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
            lenHandle.set(memorySegment, offset, data.isSet(field) ? -1 : -2);
        }
        else {
            lenHandle.set(memorySegment, offset, value.length);
            for (int j = 0; j < value.length; j++) {
                arrayHandle.set(memorySegment, offset, j, value[j]);
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        final int size = (int) lenHandle.get(memorySegment, offset);
        if (size == -1) {
            data.set(field, null);
        }
        if (size >= 0) {
            int[] value = new int[size];
            for (int i = 0; i < size; i++) {
                value[i] = (int) arrayHandle.get(memorySegment, offset, i);
            }
            data.set(field, value);
        }
    }
}
