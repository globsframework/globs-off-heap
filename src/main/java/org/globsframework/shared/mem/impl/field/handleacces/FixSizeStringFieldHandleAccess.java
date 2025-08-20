package org.globsframework.shared.mem.impl.field.handleacces;

import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.impl.DefaultOffHeapService;
import org.globsframework.shared.mem.impl.read.ReadContext;
import org.globsframework.shared.mem.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class FixSizeStringFieldHandleAccess implements HandleAccess {
    private final StringField stringField;
    private final VarHandle varLenHandle;
    private final VarHandle varStrHandle;
    private final int maxSize;
    private final boolean canTruncate;

    FixSizeStringFieldHandleAccess(StringField stringField, VarHandle varLenHandle, VarHandle varStrHandle) {
        this.stringField = stringField;
        this.varLenHandle = varLenHandle;
        this.varStrHandle = varStrHandle;
        final Glob maxSize = stringField.getAnnotation(MaxSize.KEY);
        this.maxSize = maxSize.getNotNull(MaxSize.VALUE);
        canTruncate = maxSize.isTrue(MaxSize.ALLOW_TRUNCATE);
    }

    public static FixSizeStringFieldHandleAccess create(GroupLayout groupLayout, StringField stringField) {
        final VarHandle lenHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(stringField.getName() + DefaultOffHeapService.STRING_SUFFIX_LEN));
        final VarHandle strHandle =
                groupLayout.varHandle(MemoryLayout.PathElement.groupElement(stringField.getName()),
                        MemoryLayout.PathElement.sequenceElement());
        return new FixSizeStringFieldHandleAccess(stringField, lenHandle, strHandle);
    }

    @Override
    public Field getField() {
        return stringField;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final String str = data.get(stringField);
        if (str == null) {
            varLenHandle.set(memorySegment, offset, -1);
        }
        else {
            if (str.length() > this.maxSize) {
                if (!this.canTruncate) {
                    throw new IllegalArgumentException("String '" + str + "' is too long for field '" + stringField.getFullName() + "' (max size: " + this.maxSize + ")");
                }
            }
            int len = Math.min(str.length(), this.maxSize);
            varLenHandle.set(memorySegment, offset, len);
            for (int j = 0; j < len; j++) {
                varStrHandle.set(memorySegment, offset, j, str.charAt(j));
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int len = (int) varLenHandle.get(memorySegment, offset);
        if (len == -1) {
            data.set(stringField, null);
        }
        else {
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                char c = (char) varStrHandle.get(memorySegment, offset, i);
                sb.append(c);
            }
            data.set(stringField, sb.toString());
        }
    }
}
