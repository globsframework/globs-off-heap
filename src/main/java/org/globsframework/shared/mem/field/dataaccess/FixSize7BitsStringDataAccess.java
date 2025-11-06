package org.globsframework.shared.mem.field.dataaccess;

import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.FieldValues;
import org.globsframework.shared.mem.field.handleacces.FixSizeStringFieldHandleAccess;
import org.globsframework.shared.mem.tree.impl.StringAccessorByAddress;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class FixSize7BitsStringDataAccess implements DataAccess {
    private final StringField field;
    private final VarHandle varStrHandle;
    private final FixSizeStringFieldHandleAccess.ReadLen readLen;

    public FixSize7BitsStringDataAccess(StringField field, VarHandle varLenHandle, VarHandle varStrHandle) {
        this.field = field;
        this.varStrHandle = varStrHandle;
        final int maxSize = field.getAnnotation(MaxSize.KEY).getNotNull(MaxSize.VALUE);
        if (maxSize < FixSizeStringFieldHandleAccess.ByteLenAccess.MAX_LEN) {
            readLen = new FixSizeStringFieldHandleAccess.ByteLenAccess(varLenHandle);
        } else {
            readLen = new FixSizeStringFieldHandleAccess.IntLenAccess(varLenHandle);
        }
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public int compare(FieldValues functionalKey, MemorySegment memorySegment, long index,
                       StringAccessorByAddress stringAccessorByAddress) {
        final String s = functionalKey.get(field);
        int len = readLen.readLenArray(memorySegment, index);
        if ((s == null) && (len == -1)) {
            return 0;
        }
        if (s == null) {
            return -1;
        }
        if (len == -1) {
            return 1;
        }
        int minLen = Math.min(len, s.length());
        for (int i = 0; i < minLen; i++) {
            final char c = (char) (byte) varStrHandle.get(memorySegment, 0L, index, i);
            final char c1 = s.charAt(i);
            if (c1 != c) {
                return c1 - c;
            }
        }
        return Integer.compare(s.length(), len);

    }
}
