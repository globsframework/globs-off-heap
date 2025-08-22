package org.globsframework.shared.mem.impl.field.dataaccess;

import org.globsframework.core.metamodel.fields.DateField;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.FieldValues;
import org.globsframework.shared.mem.impl.StringAccessorByAddress;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.time.LocalDate;

public class DateDataAccess implements DataAccess {
    private final DateField field;
    private final VarHandle varHandle;

    public DateDataAccess(DateField field, VarHandle varHandle) {
        this.field = field;
        this.varHandle = varHandle;
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public int compare(FieldValues functionalKey, MemorySegment memorySegment, long index, StringAccessorByAddress stringAccessorByAddress) {
        final LocalDate i = functionalKey.get(field);
        final long other = (long)varHandle.get(memorySegment, 0L, index);
        return compareDate(i, other);
    }

    static int compareDate(LocalDate i, long other) {
        int cmp = Integer.compare(i.getYear(), ((int) (other >>> 32)));
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(i.getMonthValue(), (int)((other & 0x0000_0000_FFFF_0000L) >> 16));
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(i.getDayOfMonth(), (int)((other & 0x0000_0000_0000_FFFFL)));
        return cmp;
    }

    public static DateDataAccess create(GroupLayout groupLayout, DateField field) {
        return new DateDataAccess(field, groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(field.getName())));
    }

    public static long toLong(int year, int  month, int day) {
        return ((long) year) << 32 | ((long) (month)) << 16 | (long) day;
    }

    public static LocalDate toLocalDate(long date) {
        return LocalDate.of(
                (int) (date >>> 32),
                (int)((date & 0x0000_0000_FFFF_0000L) >> 16),
                (int)((date & 0x0000_0000_0000_FFFFL)));
    }

}
