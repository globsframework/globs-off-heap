package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.fields.DateTimeField;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DateTimeFieldHandleAccess implements HandleAccess {
    private final VarHandle dateVarHandle;
    private final VarHandle timeVarHandle;
    private final VarHandle nanoVarHandle;
    private final VarHandle zoneIdVarHandle;
    private final DateTimeField field;

    public DateTimeFieldHandleAccess(VarHandle dateVarHandle, VarHandle timeVarHandle,
                                     VarHandle nanoVarHandle, VarHandle zoneIdVarHandle, DateTimeField field) {
        this.dateVarHandle = dateVarHandle;
        this.timeVarHandle = timeVarHandle;
        this.nanoVarHandle = nanoVarHandle;
        this.zoneIdVarHandle = zoneIdVarHandle;
        this.field = field;
    }

    public static HandleAccess create(GroupLayout groupLayout, DateTimeField field) {
        return new DateTimeFieldHandleAccess(
                groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapTreeService.DATE_TIME_DATE_SUFFIX)),
                groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapTreeService.DATE_TIME_TIME_SUFFIX)),
                groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapTreeService.DATE_TIME_NANO_SUFFIX)),
                groupLayout.varHandle(
                        MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapTreeService.DATE_TIME_ZONE_ID_SUFFIX),
                        MemoryLayout.PathElement.sequenceElement()),
                field);
    }

    @Override
    public Field getField() {
        return field;
    }

    @Override
    public void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext) {
        final ZonedDateTime value = data.get(field);
        if (value == null) {
            dateVarHandle.set(offset, Integer.MIN_VALUE);
            return;
        }
        int val1 = (value.getYear() & 0xFFFFF) << 9 | value.getMonthValue() << 5 | value.getDayOfMonth();
        int val2 = value.getHour() << 12 | value.getMinute() << 6 | value.getSecond();
        int nano = value.getNano();
        String zone = value.getZone().getId();

        dateVarHandle.set(memorySegment, offset, val1);
        timeVarHandle.set(memorySegment, offset, val2);
        nanoVarHandle.set(memorySegment, offset, nano);
        if (zone.length() > DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE) {
            throw new RuntimeException("Zone can not have more than " + DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE + " characters");
        }
        for (int i = 0; i < zone.length(); i++) {
            zoneIdVarHandle.set(memorySegment, offset, i, (byte)zone.charAt(i));
        }
        for (int i = zone.length(); i < DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE; i++) {
            zoneIdVarHandle.set(memorySegment, offset, i, (byte)' ');
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int val1 = (int)dateVarHandle.get(memorySegment, offset);
        if (val1 == Integer.MIN_VALUE) {
            data.set(field, null);
            return;
        }
        int val2 = (int)timeVarHandle.get(memorySegment, offset);
        int nano =  (int)nanoVarHandle.get(memorySegment, offset);
        byte[] array = new byte[DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE];
        int len = 0;
        for (int i = 0; i < DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE; i++) {
            array[i] = (byte)zoneIdVarHandle.get(memorySegment, offset, i);
            if (array[i] == ' ') {
                len = i;
                break;
            }
        }
        final ZoneId zoneId = ZoneId.of(new String(array, 0, len, StandardCharsets.US_ASCII));
        int year = (val1 >>> 9);
        int month = (val1 >>> 5) & 0xF;
        int dayOfMonth = ((int) (val1 & 0x1F));
        int hour = (val2 >>> 12);
        int minute = (val2 >>> 6) & 0x3F;
        int second = ((int) (val2 & 0x3F));
        data.set(field, ZonedDateTime.of(year, month, dayOfMonth, hour, minute, second, nano, zoneId));
    }

}
