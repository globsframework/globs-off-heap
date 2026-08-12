package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetStringAccessor;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

public class FixSize7BitsStringFieldHandleAccess implements HandleAccess {
    private final StringField stringField;
    private final VarHandle varStrHandle;
    private final long strBaseOffset;
    private final int maxSize;
    private final boolean canTruncate;
    private final FixSizeStringFieldHandleAccess.ReadLen lenAccess;
    private final GlobSetStringAccessor setAccessor;

    FixSize7BitsStringFieldHandleAccess(StringField stringField, VarHandle varLenHandle, VarHandle varStrHandle,
                                        long strBaseOffset) {
        this.stringField = stringField;
        this.varStrHandle = varStrHandle;
        this.strBaseOffset = strBaseOffset;
        final Glob maxSize = stringField.getAnnotation(MaxSize.KEY);
        this.maxSize = maxSize.getNotNull(MaxSize.VALUE);
        canTruncate = maxSize.isTrue(MaxSize.ALLOW_TRUNCATE);
        if (this.maxSize < FixSizeStringFieldHandleAccess.ByteLenAccess.MAX_LEN) {
            lenAccess = new FixSizeStringFieldHandleAccess.ByteLenAccess(varLenHandle);
        } else {
            lenAccess = new FixSizeStringFieldHandleAccess.IntLenAccess(varLenHandle);
        }
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
            for (int j = 0; j < len; j++) {
                varStrHandle.set(memorySegment, offset, j, (byte) (str.codePointAt(j) & 0xFF));
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
