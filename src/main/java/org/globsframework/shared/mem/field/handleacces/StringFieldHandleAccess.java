package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetStringAccessor;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;
import org.globsframework.shared.mem.tree.impl.write.StringRef;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class StringFieldHandleAccess implements HandleAccess {
    private final StringField stringField;
    private final long lenOffset;
    private final long addrOffset;
    private final GlobSetStringAccessor setAccessor;

    StringFieldHandleAccess(StringField stringField, long lenOffset, long addrOffset) {
        this.stringField = stringField;
        this.lenOffset = lenOffset;
        this.addrOffset = addrOffset;
        setAccessor = stringField.getGlobType().getSetAccessor(stringField);
    }

    public static StringFieldHandleAccess create(GroupLayout groupLayout, StringField stringField) {
        final long lenOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(stringField.getName() + DefaultOffHeapTreeService.STRING_SUFFIX_LEN));
        final long addrOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(stringField.getName() + DefaultOffHeapTreeService.STRING_SUFFIX_ADDR));
        return new StringFieldHandleAccess(stringField, lenOffset, addrOffset);
    }

    @Override
    public Field getField() {
        return stringField;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        if (!data.isSet(stringField)) {
            memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, -2);
            return;
        }
        final String str = data.get(stringField);
        if (str == null) {
            memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, -1);
            return;
        }
        final StringRef stringAddress = saveContext.stringAddrAccessor().get(str);
        memorySegment.set(ValueLayout.JAVA_INT, offset + lenOffset, stringAddress.len);
        memorySegment.set(ValueLayout.JAVA_INT, offset + addrOffset, stringAddress.offset);
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int len = memorySegment.get(ValueLayout.JAVA_INT, offset + lenOffset);
        if (len == -2) {
            return;
        }
        if (len == -1) {
            setAccessor.set(data, null);
            return;
        }
        int addr = memorySegment.get(ValueLayout.JAVA_INT, offset + addrOffset);
        setAccessor.set(data, readContext.get(addr, len));
    }
}
