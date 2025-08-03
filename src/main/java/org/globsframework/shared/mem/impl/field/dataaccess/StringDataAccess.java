package org.globsframework.shared.mem.impl.field.dataaccess;

import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.FieldValues;
import org.globsframework.core.utils.Utils;
import org.globsframework.shared.mem.impl.DefaultOffHeapService;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;

public class StringDataAccess implements DataAccess {
    private final StringField field;
    private final VarHandle varAddrHandle;
    private final VarHandle varLenHandle;

    public StringDataAccess(StringField field, VarHandle varAddrHandle, VarHandle varLenHandle) {
        this.field = field;
        this.varAddrHandle = varAddrHandle;
        this.varLenHandle = varLenHandle;
    }

    public static DataAccess create(GroupLayout groupLayout, StringField field) {
        return new StringDataAccess(field, groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapService.STRING_SUFFIX_ADDR)),
                groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapService.STRING_SUFFIX_LEN)));
    }

    public String get(MemorySegment memorySegment, long offset, StringAccessorByAddress stringAccessorByAddress) {
        int addr = (int) varAddrHandle.get(memorySegment, 0L, offset);
        int len = (int) varLenHandle.get(memorySegment, 0L, offset);
        return stringAccessorByAddress.get(addr, len);
    }

    @Override
    public int compare(FieldValues functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress) {
        return Utils.compare(functionalKey.get(field), get(memorySegment, index, stringAccessorByAddress));
    }
}
