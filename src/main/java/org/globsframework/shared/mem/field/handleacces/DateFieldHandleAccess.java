package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.fields.DateField;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.field.dataaccess.DateDataAccess;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.time.LocalDate;

public class DateFieldHandleAccess implements HandleAccess {
    private final DateField dateField;
    private final VarHandle longVarHandle;

    DateFieldHandleAccess(DateField dateField, VarHandle longVarHandle) {
        this.dateField = dateField;
        this.longVarHandle = longVarHandle;
    }

    public static DateFieldHandleAccess create(GroupLayout groupLayout, DateField dateField) {
        final VarHandle addHandle = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(dateField.getName()));
        return new DateFieldHandleAccess(dateField, addHandle);
    }

    @Override
    public Field getField() {
        return dateField;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final LocalDate date = data.get(dateField);
        if (date == null) {
            longVarHandle.set(memorySegment, offset, Long.MAX_VALUE);
        }
        else {
            final long longDate = DateDataAccess.toLong(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            longVarHandle.set(memorySegment, offset, longDate);
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        long longDate = (long) longVarHandle.get(memorySegment, offset);
        if (longDate != Long.MAX_VALUE) {
            data.set(dateField, DateDataAccess.toLocalDate(longDate));
        }
        else {
            data.set(dateField, null);
        }
    }
}
