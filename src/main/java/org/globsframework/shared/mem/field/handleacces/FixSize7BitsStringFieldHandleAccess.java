package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class FixSize7BitsStringFieldHandleAccess implements HandleAccess {
    private final StringField stringField;
    private final VarHandle varStrHandle;
    private final int maxSize;
    private final boolean canTruncate;
    private final FixSizeStringFieldHandleAccess.ReadLen lenAccess;

    FixSize7BitsStringFieldHandleAccess(StringField stringField, VarHandle varLenHandle, VarHandle varStrHandle) {
        this.stringField = stringField;
        this.varStrHandle = varStrHandle;
        final Glob maxSize = stringField.getAnnotation(MaxSize.KEY);
        this.maxSize = maxSize.getNotNull(MaxSize.VALUE);
        canTruncate = maxSize.isTrue(MaxSize.ALLOW_TRUNCATE);
        if (this.maxSize < FixSizeStringFieldHandleAccess.ByteLenAccess.MAX_LEN) {
            lenAccess = new FixSizeStringFieldHandleAccess.ByteLenAccess(varLenHandle);
        } else {
            lenAccess = new FixSizeStringFieldHandleAccess.IntLenAccess(varLenHandle);
        }

    }

    @Override
    public Field getField() {
        return stringField;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final String str = data.get(stringField);
        if (str == null) {
            lenAccess.writeLen(memorySegment, offset, -1);
        } else {
            if (str.length() > this.maxSize) {
                if (!this.canTruncate) {
                    throw new IllegalArgumentException("String '" + str + "' is too long for field '" + stringField.getFullName() + "' (max size: " + this.maxSize + ")");
                }
            }
            int len = Math.min(str.length(), this.maxSize);
            lenAccess.writeLen(memorySegment, offset, len);
            for (int j = 0; j < len; j++) {
                varStrHandle.set(memorySegment, offset, j, (byte) (str.codePointAt(j) & 0xFF));
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int len = lenAccess.readLen(memorySegment, offset);
        if (len == -1) {
            data.set(stringField, null);
        } else {
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                byte c = (byte) varStrHandle.get(memorySegment, offset, i);
                sb.append(((char) (c & 0xFF)));
            }
            data.set(stringField, sb.toString());
        }
    }
}
