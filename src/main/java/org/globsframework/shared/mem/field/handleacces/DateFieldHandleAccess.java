package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.fields.DateField;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetDateAccessor;
import org.globsframework.shared.mem.field.dataaccess.DateDataAccess;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.time.LocalDate;

public final class DateFieldHandleAccess implements HandleAccess {
    private final DateField dateField;
    private final long fieldOffset;
    private final GlobSetDateAccessor setAccessor;

    DateFieldHandleAccess(DateField dateField, long fieldOffset) {
        this.dateField = dateField;
        this.fieldOffset = fieldOffset;
        setAccessor = dateField.getGlobType().getSetAccessor(dateField);
    }

    public static DateFieldHandleAccess create(GroupLayout groupLayout, DateField dateField) {
        final long fieldOffset = groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(dateField.getName()));
        return new DateFieldHandleAccess(dateField, fieldOffset);
    }

    @Override
    public Field getField() {
        return dateField;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final LocalDate date = data.get(dateField);
        if (date == null) {
            memorySegment.set(ValueLayout.JAVA_LONG, offset + fieldOffset, Long.MAX_VALUE);
        }
        else {
            final long longDate = DateDataAccess.toLong(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
            memorySegment.set(ValueLayout.JAVA_LONG, offset + fieldOffset, longDate);
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        long longDate = memorySegment.get(ValueLayout.JAVA_LONG, offset + fieldOffset);
        if (longDate != Long.MAX_VALUE) {
            setAccessor.set(data, DateDataAccess.toLocalDate(longDate));
        }
        else {
            setAccessor.set(data, null);
        }
    }
}
