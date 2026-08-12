package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetStringAccessor;
import org.globsframework.shared.mem.model.Heap7BitsString;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;

public final class FixSizeStringFieldHandleAccess implements HandleAccess {
    private final StringField stringField;
    private final long strBaseOffset;
    private final int maxSize;
    private final boolean canTruncate;
    private final RecordLen lenAccess;
    private final GlobSetStringAccessor setAccessor;

    FixSizeStringFieldHandleAccess(StringField stringField, long lenOffset, long strBaseOffset) {
        this.stringField = stringField;
        this.strBaseOffset = strBaseOffset;
        final Glob maxSize = stringField.getAnnotation(MaxSize.KEY);
        this.maxSize = maxSize.getNotNull(MaxSize.VALUE);
        canTruncate = maxSize.isTrue(MaxSize.ALLOW_TRUNCATE);
        this.lenAccess = createLenAccess(this.maxSize, lenOffset);
        setAccessor = stringField.getGlobType().getSetAccessor(stringField);
    }

    /**
     * The length slot of a record, addressed by byte offset. The index side has its own reader
     * ({@link ReadLen}) because it addresses a record inside a sequence through an indexed VarHandle ; here
     * the offset is known at create() time, so the layout constant is enough and nothing goes through a
     * VarHandle whose receiver the JIT cannot fold.
     */
    public interface RecordLen {
        int readLen(MemorySegment memorySegment, long offset);

        void writeLen(MemorySegment memorySegment, long offset, int len);
    }

    static RecordLen createLenAccess(int maxSize, long lenOffset) {
        return maxSize < ByteLenAccess.MAX_LEN ? new ByteRecordLen(lenOffset) : new IntRecordLen(lenOffset);
    }

    /** the same slot, reached from the index records, where the offset is not known ahead of the descent */
    public interface ReadLen {
        int readLen(MemorySegment memorySegment, long index);

        int readLenArray(MemorySegment memorySegment, long index);

        void writeLen(MemorySegment memorySegment, long index, int len);
    }

    public static HandleAccess create(GroupLayout groupLayout, StringField stringField) {
        final long lenOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(stringField.getName() + DefaultOffHeapTreeService.STRING_SUFFIX_LEN));
        // the byte offset of the character sequence inside the record : what a bulk copy needs, where the
        // indexed VarHandle only gave one element at a time
        final long strBaseOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(stringField.getName()));
        if (stringField.hasAnnotation(Heap7BitsString.UNIQUE_KEY)) {
            return new FixSize7BitsStringFieldHandleAccess(stringField, lenOffset, strBaseOffset);
        } else {
            return new FixSizeStringFieldHandleAccess(stringField, lenOffset, strBaseOffset);
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
        }
        else {
            if (str.length() > this.maxSize) {
                if (!this.canTruncate) {
                    throw new IllegalArgumentException("String '" + str + "' is too long for field '" + stringField.getFullName() + "' (max size: " + this.maxSize + ")");
                }
            }
            int len = Math.min(str.length(), this.maxSize);
            lenAccess.writeLen(memorySegment, offset, len);
            final long base = offset + strBaseOffset;
            for (int j = 0; j < len; j++) {
                memorySegment.set(ValueLayout.JAVA_CHAR, base + j * 2L, str.charAt(j));
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int len = lenAccess.readLen(memorySegment, offset);
        if (len == -1) {
            setAccessor.set(data, null);
        }
        else if (len == 0) {
            setAccessor.set(data, "");
        }
        else {
            // one bulk copy rather than len VarHandle calls into a StringBuilder, which allocated its own
            // buffer and then copied it again in toString()
            final char[] chars = new char[len];
            MemorySegment.copy(memorySegment, ValueLayout.JAVA_CHAR, offset + strBaseOffset, chars, 0, len);
            setAccessor.set(data, new String(chars, 0, len));
        }
    }

    static final class ByteRecordLen implements RecordLen {
        private final long lenOffset;

        ByteRecordLen(long lenOffset) {
            this.lenOffset = lenOffset;
        }

        @Override
        public int readLen(MemorySegment memorySegment, long offset) {
            return (memorySegment.get(ValueLayout.JAVA_BYTE, offset + lenOffset) & 0xFF) - 1;
        }

        @Override
        public void writeLen(MemorySegment memorySegment, long offset, int len) {
            memorySegment.set(ValueLayout.JAVA_BYTE, offset + lenOffset, (byte) (len + 1));
        }
    }

    static final class IntRecordLen implements RecordLen {
        private final long lenOffset;

        IntRecordLen(long lenOffset) {
            this.lenOffset = lenOffset;
        }

        @Override
        public int readLen(MemorySegment memorySegment, long offset) {
            return memorySegment.get(ValueLayout.JAVA_INT, offset + lenOffset);
        }

        @Override
        public void writeLen(MemorySegment memorySegment, long offset, int len) {
            memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, len);
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
