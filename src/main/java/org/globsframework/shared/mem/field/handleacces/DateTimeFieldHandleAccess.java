package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.fields.DateTimeField;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetDateTimeAccessor;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class DateTimeFieldHandleAccess implements HandleAccess {
    private final long dateOffset;
    private final long timeOffset;
    private final long nanoOffset;
    private final long zoneIdBaseOffset;
    private final DateTimeField field;
    private final GlobSetDateTimeAccessor setAccessor;

    /**
     * Last zone id decoded, kept as the raw bytes next to the ZoneId they parse to. Records overwhelmingly
     * share one zone, and ZoneId.of is a String allocation plus a map lookup plus validation -- comparing a
     * few bytes is far cheaper. Volatile and immutable : a race just recomputes, it cannot publish a
     * half-built pair.
     */
    private volatile CachedZone cachedZone = new CachedZone(new byte[0], null);

    private record CachedZone(byte[] bytes, ZoneId zoneId) {
        boolean matches(byte[] other, int len) {
            return bytes.length == len && Arrays.equals(bytes, 0, len, other, 0, len);
        }
    }

    public DateTimeFieldHandleAccess(long dateOffset, long timeOffset,
                                     long nanoOffset, long zoneIdBaseOffset,
                                     DateTimeField field) {
        this.dateOffset = dateOffset;
        this.timeOffset = timeOffset;
        this.nanoOffset = nanoOffset;
        this.zoneIdBaseOffset = zoneIdBaseOffset;
        this.field = field;
        setAccessor = field.getGlobType().getSetAccessor(field);
    }

    public static HandleAccess create(GroupLayout groupLayout, DateTimeField field) {
        return new DateTimeFieldHandleAccess(
                groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapTreeService.DATE_TIME_DATE_SUFFIX)),
                groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapTreeService.DATE_TIME_TIME_SUFFIX)),
                groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(field.getName() + DefaultOffHeapTreeService.DATE_TIME_NANO_SUFFIX)),
                groupLayout.byteOffset(MemoryLayout.PathElement.groupElement(
                        field.getName() + DefaultOffHeapTreeService.DATE_TIME_ZONE_ID_SUFFIX)),
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
            memorySegment.set(ValueLayout.JAVA_INT, offset + dateOffset, Integer.MIN_VALUE);
            return;
        }
        int val1 = (value.getYear() & 0xFFFFF) << 9 | value.getMonthValue() << 5 | value.getDayOfMonth();
        int val2 = value.getHour() << 12 | value.getMinute() << 6 | value.getSecond();
        int nano = value.getNano();
        String zone = value.getZone().getId();

        memorySegment.set(ValueLayout.JAVA_INT, offset + dateOffset, val1);
        memorySegment.set(ValueLayout.JAVA_INT, offset + timeOffset, val2);
        memorySegment.set(ValueLayout.JAVA_INT, offset + nanoOffset, nano);
        if (zone.length() > DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE) {
            throw new RuntimeException("Zone can not have more than " + DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE + " characters");
        }
        final long zoneBase = offset + zoneIdBaseOffset;
        for (int i = 0; i < zone.length(); i++) {
            memorySegment.set(ValueLayout.JAVA_BYTE, zoneBase + i, (byte) zone.charAt(i));
        }
        for (int i = zone.length(); i < DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE; i++) {
            memorySegment.set(ValueLayout.JAVA_BYTE, zoneBase + i, (byte) ' ');
        }
    }

    @Override
    public void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        int val1 = memorySegment.get(ValueLayout.JAVA_INT, offset + dateOffset);
        if (val1 == Integer.MIN_VALUE) {
            data.set(field, null);
            return;
        }
        int val2 = memorySegment.get(ValueLayout.JAVA_INT, offset + timeOffset);
        int nano = memorySegment.get(ValueLayout.JAVA_INT, offset + nanoOffset);
        // one bulk copy rather than up to 52 VarHandle calls, then the space padding tells the real length
        byte[] array = new byte[DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE];
        MemorySegment.copy(memorySegment, ValueLayout.JAVA_BYTE, offset + zoneIdBaseOffset, array, 0,
                DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE);
        int len = DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE;
        for (int i = 0; i < DefaultOffHeapTreeService.DATE_TIME_MAX_ZONE_ID_SIZE; i++) {
            if (array[i] == ' ') {
                len = i;
                break;
            }
        }
        final CachedZone cached = cachedZone;
        final ZoneId zoneId;
        if (cached.matches(array, len)) {
            zoneId = cached.zoneId();
        } else {
            zoneId = ZoneId.of(new String(array, 0, len, StandardCharsets.US_ASCII));
            cachedZone = new CachedZone(Arrays.copyOf(array, len), zoneId);
        }
        int year = (val1 >>> 9);
        int month = (val1 >>> 5) & 0xF;
        int dayOfMonth = ((int) (val1 & 0x1F));
        int hour = (val2 >>> 12);
        int minute = (val2 >>> 6) & 0x3F;
        int second = ((int) (val2 & 0x3F));
        setAccessor.set(data, ZonedDateTime.of(year, month, dayOfMonth, hour, minute, second, nano, zoneId));
    }

}
