package org.globsframework.shared.mem.impl.field.dataaccess;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.FieldValues;
import org.globsframework.shared.mem.impl.DefaultOffHeapService;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class FixSizeStringDataAccess implements DataAccess {
    private final StringField field;
    private final VarHandle varStrHandle;
    private final VarHandle varLenHandle;

    public FixSizeStringDataAccess(StringField field, VarHandle varLenHandle, VarHandle varStrHandle) {
        this.field = field;
        this.varLenHandle = varLenHandle;
        this.varStrHandle = varStrHandle;
    }

    public static DataAccess create(GroupLayout groupLayout, StringField field) {
        return new FixSizeStringDataAccess(field,
                groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapService.STRING_SUFFIX_LEN)),
                groupLayout.arrayElementVarHandle(
                        MemoryLayout.PathElement.groupElement(field.getName()),
                        MemoryLayout.PathElement.sequenceElement()));
    }


    @Override
    public Field getField() {
        return field;
    }

    @Override
    public int compare(FieldValues functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress) {
        final String s = functionalKey.get(field);
        int len = (int) varLenHandle.get(memorySegment, 0L, index);
        if ((s == null) && (len == -1)) {
            return 0;
        }
        if (s == null) {
            return -1;
        }
        if (len == -1) {
            return 1;
        }
        // TODO : apply same code as String.compare?
        StringBuilder stringBuilder = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            stringBuilder.append((char) varStrHandle.get(memorySegment, 0L, index, i));
        }
        return s.compareTo(stringBuilder.toString());
    }
}
