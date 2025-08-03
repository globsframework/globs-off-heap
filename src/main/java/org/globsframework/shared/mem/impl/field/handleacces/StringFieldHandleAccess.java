package org.globsframework.shared.mem.impl.field.handleacces;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.impl.DefaultOffHeapService;
import org.globsframework.shared.mem.impl.read.ReadContext;
import org.globsframework.shared.mem.impl.write.SaveContext;
import org.globsframework.shared.mem.impl.write.StringRefType;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class StringFieldHandleAccess implements HandleAccess {
    private final StringField stringField;
    private final VarHandle varLenHandle;
    private final VarHandle varAddrHandle;

    StringFieldHandleAccess(StringField stringField, VarHandle varLenHandle, VarHandle varAddrHandle) {
        this.stringField = stringField;
        this.varLenHandle = varLenHandle;
        this.varAddrHandle = varAddrHandle;
    }

    public static StringFieldHandleAccess create(GroupLayout groupLayout, StringField stringField) {
        final VarHandle addHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(stringField.getName() + DefaultOffHeapService.STRING_SUFFIX_ADDR));
        final VarHandle lenHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(stringField.getName() + DefaultOffHeapService.STRING_SUFFIX_LEN));
        return new StringFieldHandleAccess(stringField, lenHandle, addHandle);
    }

    @Override
    public Field getField() {
        return stringField;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final String str = data.get(stringField);
        final Glob stringAddress = saveContext.stringAddrAccessor().get(str);
        varLenHandle.set(memorySegment, offset, stringAddress.get(StringRefType.len));
        varAddrHandle.set(memorySegment, offset, stringAddress.get(StringRefType.offset));
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int addr = (int) varAddrHandle.get(memorySegment, offset);
        int len = (int) varLenHandle.get(memorySegment, offset);
        data.set(stringField, readContext.stringAccessorByAddress().get(addr, len));
    }
}
