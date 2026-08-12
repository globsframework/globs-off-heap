package org.globsframework.shared.mem.tree;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.ArraySize;
import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.DataConsumer;
import org.globsframework.shared.mem.model.Heap7BitsString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The reads that stopped going through the memory one element at a time : the two fixed-size string kinds,
 * the int array, and the zone id of a DateTime. Bulk copies have to say exactly what the per-element loops
 * said, boundaries included — an empty string, the full width, a byte above 127 on the "7 bits" kind (save
 * masks with 0xFF, so the range is really Latin-1).
 * <p>
 * The zone id also gained a one-entry memo, since ZoneId.of costs a String and a lookup and records almost
 * always share a zone. {@link #alternatingZoneIdsAreNotConfused} is what holds it honest.
 */
public class BulkReadRoundTripTest {

    public static class Data {
        public static final GlobType TYPE;
        public static final IntegerField id;
        public static final StringField sevenBits;
        public static final StringField utf16;
        public static final IntegerArrayField values;
        public static final DateTimeField dateTime;

        static {
            GlobTypeBuilder builder = GlobTypeBuilderFactory.create("BulkData");
            id = builder.declareIntegerField("id");
            sevenBits = builder.declareStringField("sevenBits", MaxSize.create(16, true),
                    Heap7BitsString.UNIQUE_GLOB);
            utf16 = builder.declareStringField("utf16", MaxSize.create(16, true));
            values = builder.declareIntegerArrayField("values", ArraySize.create(4));
            dateTime = builder.declareDateTimeField("dateTime");
            TYPE = builder.build();
        }
    }

    private List<Glob> readBack(List<Glob> written) throws IOException {
        OffHeapTreeService service = OffHeapTreeService.create(Data.TYPE);
        Path path = Files.createTempDirectory("bulkread");
        service.createWrite(path).save(written);

        List<Glob> read = new ArrayList<>();
        try (Arena arena = Arena.ofShared()) {
            OffHeapReadTreeService readService = service.createRead(path, arena);
            readService.readAll(glob -> {
                read.add(glob.duplicate());
                return true;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return read;
    }

    @Test
    public void fixedSizeStringsAtTheirBoundaries() throws IOException {
        List<Glob> written = new ArrayList<>();
        String[] samples = {"", "a", "0123456789abcdef", "éÿ", "abc"};
        for (int i = 0; i < samples.length; i++) {
            written.add(Data.TYPE.instantiate()
                    .set(Data.id, i)
                    .set(Data.sevenBits, samples[i])
                    .set(Data.utf16, samples[i])
                    .set(Data.values, new int[]{i, i + 1, i + 2, i + 3}));
        }

        List<Glob> read = readBack(written);
        Assertions.assertEquals(samples.length, read.size());
        for (Glob glob : read) {
            int i = glob.get(Data.id);
            Assertions.assertEquals(samples[i], glob.get(Data.sevenBits), "sevenBits #" + i);
            Assertions.assertEquals(samples[i], glob.get(Data.utf16), "utf16 #" + i);
            Assertions.assertArrayEquals(new int[]{i, i + 1, i + 2, i + 3}, glob.get(Data.values), "values #" + i);
        }
    }

    /** null stays null, and is not confused with the empty string */
    @Test
    public void nullAndEmptyStayApart() throws IOException {
        List<Glob> read = readBack(List.of(
                Data.TYPE.instantiate().set(Data.id, 0).set(Data.sevenBits, null).set(Data.utf16, null),
                Data.TYPE.instantiate().set(Data.id, 1).set(Data.sevenBits, "").set(Data.utf16, "")));

        Assertions.assertNull(read.get(0).get(Data.sevenBits));
        Assertions.assertNull(read.get(0).get(Data.utf16));
        Assertions.assertEquals("", read.get(1).get(Data.sevenBits));
        Assertions.assertEquals("", read.get(1).get(Data.utf16));
    }

    /**
     * The zone id memo holds one entry, so a dataset that alternates zones exercises every miss. Long and
     * short ids on purpose : the cached bytes are compared on the decoded length, and a short id following a
     * long one must not match on a prefix.
     */
    @Test
    public void alternatingZoneIdsAreNotConfused() throws IOException {
        String[] zones = {"Europe/Paris", "UTC", "America/Argentina/Buenos_Aires", "UTC", "Asia/Tokyo"};
        List<Glob> written = new ArrayList<>();
        for (int i = 0; i < zones.length; i++) {
            written.add(Data.TYPE.instantiate()
                    .set(Data.id, i)
                    .set(Data.dateTime, ZonedDateTime.of(2026, 8, 12, 10, 30, 15, 0, ZoneId.of(zones[i]))));
        }

        List<Glob> read = readBack(written);
        Assertions.assertEquals(zones.length, read.size());
        for (Glob glob : read) {
            int i = glob.get(Data.id);
            ZonedDateTime value = glob.get(Data.dateTime);
            Assertions.assertNotNull(value, "#" + i);
            Assertions.assertEquals(ZoneId.of(zones[i]), value.getZone(), "#" + i);
            Assertions.assertEquals(ZonedDateTime.of(2026, 8, 12, 10, 30, 15, 0, ZoneId.of(zones[i])), value, "#" + i);
        }
    }
}
