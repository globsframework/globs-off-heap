package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class FixSizeStringFieldHandleAccess implements HandleAccess {
    private final StringField stringField;
    private final VarHandle varStrHandle;
    private final int maxSize;
    private final boolean canTruncate;
    private final ReadLen lenAccess;

    FixSizeStringFieldHandleAccess(StringField stringField, VarHandle varLenHandle, VarHandle varStrHandle) {
        this.stringField = stringField;
        this.varStrHandle = varStrHandle;
        final Glob maxSize = stringField.getAnnotation(MaxSize.KEY);
        this.maxSize = maxSize.getNotNull(MaxSize.VALUE);
        canTruncate = maxSize.isTrue(MaxSize.ALLOW_TRUNCATE);
        if (this.maxSize < ByteLenAccess.MAX_LEN) {
            lenAccess = new ByteLenAccess(varLenHandle);
        }
        else {
            lenAccess = new IntLenAccess(varLenHandle);
        }

    }


    public interface ReadLen {
        int readLen(MemorySegment memorySegment, long index);
        int readLenArray(MemorySegment memorySegment, long index);
        void writeLen(MemorySegment memorySegment, long index, int len);
    }

    public static FixSizeStringFieldHandleAccess create(GroupLayout groupLayout, StringField stringField) {
        final VarHandle lenHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(stringField.getName() + DefaultOffHeapTreeService.STRING_SUFFIX_LEN));
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
            lenAccess.writeLen(memorySegment, offset, -1);
        }
        else {
            if (str.length() > this.maxSize) {
                if (!this.canTruncate) {
                    throw new IllegalArgumentException("String '" + str + "' is too long for field '" + stringField.getFullName() + "' (max size: " + this.maxSize + ")");
                }
            }
            int len = Math.min(str.length(), this.maxSize);
            lenAccess.writeLen(memorySegment, offset, len);
            for (int j = 0; j < len; j++) {
                varStrHandle.set(memorySegment, offset, j, str.charAt(j));
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int len = lenAccess.readLen(memorySegment, offset);
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

    public static class ByteLenAccess implements ReadLen {
        public static int MAX_LEN = 254;
        private final VarHandle varLenHandle;

        public ByteLenAccess(VarHandle varLenHandle) {
            this.varLenHandle = varLenHandle;
        }

        @Override
        public int readLen(MemorySegment memorySegment, long index) {
            return (((byte) varLenHandle.get(memorySegment, index)) & 0xFF) - 1;
        }

        @Override
        public int readLenArray(MemorySegment memorySegment, long index) {
            return (((byte) varLenHandle.get(memorySegment, 0L, index)) & 0xFF) - 1;
        }

        @Override
        public void writeLen(MemorySegment memorySegment, long index, int len) {
            varLenHandle.set(memorySegment,index, (byte) (len + 1));
        }
    }

    public static class IntLenAccess implements ReadLen {
        private final VarHandle varLenHandle;

        public IntLenAccess(VarHandle varLenHandle) {
            this.varLenHandle = varLenHandle;
        }

        @Override
        public int readLen(MemorySegment memorySegment, long index) {
            return (int) varLenHandle.get(memorySegment, index);
        }

        @Override
        public int readLenArray(MemorySegment memorySegment, long index) {
            return (int) varLenHandle.get(memorySegment, 0L, index);
        }

        @Override
        public void writeLen(MemorySegment memorySegment, long index, int len) {
            varLenHandle.set(memorySegment,index, len);
        }
    }
}
