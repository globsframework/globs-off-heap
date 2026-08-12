package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetStringAccessor;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

public final class FixSize7BitsStringFieldHandleAccess implements HandleAccess {
    private final StringField stringField;
    private final long strBaseOffset;
    private final int maxSize;
    private final boolean canTruncate;
    private final FixSizeStringFieldHandleAccess.RecordLen lenAccess;
    private final GlobSetStringAccessor setAccessor;

    FixSize7BitsStringFieldHandleAccess(StringField stringField, long lenOffset, long strBaseOffset) {
        this.stringField = stringField;
        this.strBaseOffset = strBaseOffset;
        final Glob maxSize = stringField.getAnnotation(MaxSize.KEY);
        this.maxSize = maxSize.getNotNull(MaxSize.VALUE);
        canTruncate = maxSize.isTrue(MaxSize.ALLOW_TRUNCATE);
        this.lenAccess = FixSizeStringFieldHandleAccess.createLenAccess(this.maxSize, lenOffset);
        setAccessor = stringField.getGlobType().getSetAccessor(stringField);
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
            final long base = offset + strBaseOffset;
            for (int j = 0; j < len; j++) {
                memorySegment.set(ValueLayout.JAVA_BYTE, base + j, (byte) (str.codePointAt(j) & 0xFF));
            }
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int len = lenAccess.readLen(memorySegment, offset);
        if (len == -1) {
            setAccessor.set(data, null);
        } else if (len == 0) {
            setAccessor.set(data, "");
        } else {
            // one bulk copy rather than len VarHandle calls into a StringBuilder. save() writes
            // codePointAt(j) & 0xFF and this used to read back (char) (c & 0xFF) : that is Latin-1 exactly,
            // and it is also the compact-String fast path, so the String wraps the bytes with no widening.
            final byte[] bytes = new byte[len];
            MemorySegment.copy(memorySegment, ValueLayout.JAVA_BYTE, offset + strBaseOffset, bytes, 0, len);
            setAccessor.set(data, new String(bytes, 0, len, StandardCharsets.ISO_8859_1));
        }
    }
}
