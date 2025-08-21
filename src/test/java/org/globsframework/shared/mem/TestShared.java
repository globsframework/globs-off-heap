package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.ArraySize;
import org.globsframework.core.metamodel.annotations.ArraySize_;
import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.annotations.MaxSize_;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestShared {
//    public static final int SIZE = 10_000_000;
//    public static final int MODULO = 1_000_000;
    public static final int SIZE = 500_000;
    public static final int MODULO = 50_000;
//    public static final int SIZE = 5_000;
//    public static final int MODULO = 500;

    @Test
    public void buildApi() throws IOException {

        List<Glob> globs = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            final LocalDate localDate = LocalDate.of(1900 + (i % 200), i % 11 + 1, i % 28 + 1);
            globs.add(createData(i, localDate));
        }

        final FunctionalKeyBuilder uniqueFunctionalKeyBuilder =
                FunctionalKeyBuilderFactory.create(DummyObject1.TYPE)
                        .add(DummyObject1.val1)
                        .add(DummyObject1.val2)
                        .create();

        final FunctionalKeyBuilder multiFunctionalKeyBuilder =
                FunctionalKeyBuilderFactory.create(DummyObject1.TYPE)
                        .add(DummyObject1.name)
                        .create();


        OffHeapService offHeapService = OffHeapService.create(DummyObject1.TYPE);
        OffHeapUniqueIndex offHeapUniqueIndex = offHeapService.declareUniqueIndex("uniqueIndex", uniqueFunctionalKeyBuilder);
        OffHeapNotUniqueIndex offHeapMultiIndex = offHeapService.declareNotUniqueIndex("multiIndex", multiFunctionalKeyBuilder);


        Arena arena = Arena.ofShared();

        final Path path = Path.of("/tmp/test");
        Files.createDirectories(path);
        OffHeapWriteService offHeapWriteService =
                offHeapService.createWrite(path);

        final long startWrite = System.currentTimeMillis();
        offHeapWriteService.save(globs);
        final long endWrite = System.currentTimeMillis();
        System.out.println("write " + (endWrite - startWrite) + " ms");

        offHeapWriteService.close();

        long startInitReader = System.nanoTime();
        OffHeapReadService readHeapService = offHeapService.createRead(
                Path.of("/tmp/test"), arena);

        long endInitReader = System.nanoTime();
        System.out.println("init read " + TimeUnit.NANOSECONDS.toMillis(endInitReader - startInitReader) + " ms");

        for (int i = 0; i < 20; i++) {
            loopRead(readHeapService, globs);
        }
        System.out.println("read selected");
        for (int i = 0; i < 20; i++) {
            selectRead(readHeapService, globs);
        }
        FunctionalKey[] functionalKeys = new FunctionalKey[1000];
        final FunctionalKeyBuilder optFunctionalKeyBuilder =
                FunctionalKeyBuilderFactory.create(DummyObject1.TYPE)
                        .add(DummyObject1.val1)
                        .add(DummyObject1.val2)
                        .add(DummyObject1.name)
                        .create();

        for (int i = 0; i < 1000; i++) {
            final FunctionalKey functionalKey = optFunctionalKeyBuilder.create()
                    .set(DummyObject1.val1, i)
                    .set(DummyObject1.val2, (i % MODULO))
                    .set(DummyObject1.name, "a name " + (i % MODULO))
                    .create();
            functionalKeys[i] = functionalKey;
        }
        System.gc();
        System.gc();
        System.gc();
        System.out.println("Init functionnal key ok");
        final int loop = 100;

        for (int i = 0; i < 10; i++) {
            findByIndex(loop, functionalKeys, readHeapService, offHeapUniqueIndex);
        }
        for (int i = 0; i < 10; i++) {
            getMultiIndex(readHeapService, offHeapMultiIndex, loop, functionalKeys);
        }
    }

    private static MutableGlob createData(int i, LocalDate localDate) {
        return DummyObject1.TYPE.instantiate()
                .set(DummyObject1.val1, i)
                .set(DummyObject1.val2, (i % MODULO))
                .set(DummyObject1.name, "a name " + (i % MODULO))
                .set(DummyObject1.data1, 10 + i % 100)
                .set(DummyObject1.data2, 10 + i % 100)
                .set(DummyObject1.data3, localDate)
                .set(DummyObject1.data4, ZonedDateTime.of(localDate, LocalTime.of(i % 24, i % 60, 1 % 60), ZoneId.systemDefault()))
                .set(DummyObject1.maxValue, 10 + i % 100)
                .set(DummyObject1.data, new int[]{i, i + 1, i + 2})
                .set(DummyObject1.data5, i % 2 == 0)
                .set(DummyObject1.data6, (long) i * i)
                .set(DummyObject1.fixSizeStrAllowTruncate, "\u24FF\uFD34zdfeéd32" + i) // 10 chars with not latin1 char.
                .set(DummyObject1.fixSizeStrNoTruncate, "\u63FF\uAF34" + i);  // 22 chars with not latin1 char
    }

    @Test
    public void testStringWithFixSize() throws IOException {
        List<Glob> globs = new ArrayList<>();
        globs.add(createData(1, LocalDate.of(1900, 1, 1)));
        globs.add(createData(1000, LocalDate.of(1900, 1, 1)));
        globs.add(createData(1_000_000, LocalDate.of(1900, 1, 1)));

        final FunctionalKeyBuilder uniqueFunctionalKeyBuilder =
                FunctionalKeyBuilderFactory.create(DummyObject1.TYPE)
                        .add(DummyObject1.fixSizeStrNoTruncate)
                        .create();

        OffHeapService offHeapService = OffHeapService.create(DummyObject1.TYPE);
        OffHeapUniqueIndex offHeapUniqueIndex = offHeapService.declareUniqueIndex("uniqueIndex", uniqueFunctionalKeyBuilder);


        Arena arena = Arena.ofShared();

        final Path path = Path.of("/tmp/test");
        Files.createDirectories(path);
        OffHeapWriteService offHeapWriteService =
                offHeapService.createWrite(path);

        offHeapWriteService.save(globs);

        offHeapWriteService.close();
        OffHeapReadService readHeapService = offHeapService.createRead(
                Path.of("/tmp/test"), arena);

        List<Glob> readData = new ArrayList<>();
        readHeapService.readAll(glob -> readData.add(glob));
        Assertions.assertEquals(3, globs.size());
        Assertions.assertEquals("\u63FF\uAF341", globs.get(0).get(DummyObject1.fixSizeStrNoTruncate));
        final ReadOffHeapUniqueIndex index = readHeapService.getIndex(offHeapUniqueIndex);

        check(index, uniqueFunctionalKeyBuilder, readHeapService, 1);
        check(index, uniqueFunctionalKeyBuilder, readHeapService, 1000);
        check(index, uniqueFunctionalKeyBuilder, readHeapService, 1_000_000);
    }

    private static void check(ReadOffHeapUniqueIndex index, FunctionalKeyBuilder uniqueFunctionalKeyBuilder, OffHeapReadService readHeapService, int pos) {
        final OffHeapRef offHeapRef = index.find(uniqueFunctionalKeyBuilder.create().set(DummyObject1.fixSizeStrNoTruncate, "\u63FF\uAF34" + pos).getShared());
        Assertions.assertNotNull(offHeapRef);
        final Glob read = readHeapService.read(offHeapRef).get();
        Assertions.assertEquals(pos, read.get(DummyObject1.val1).intValue());
    }

    private static void getMultiIndex(OffHeapReadService readHeapService, OffHeapNotUniqueIndex offHeapMultiIndex, int loop, FunctionalKey[] functionalKeys) {
        ReadOffHeapMultiIndex readOffHeapMultiIndex = readHeapService.getIndex(offHeapMultiIndex);
        System.out.println("start multi");
        long startIndex = System.nanoTime();
        for (int i = 0; i < loop; i++) {
            {
                for (FunctionalKey functionalKey : functionalKeys) {
                    final OffHeapRefs offHeapRefs = readOffHeapMultiIndex.find(functionalKey);
                    Assertions.assertNotNull(offHeapRefs, functionalKey.toString());
                    AtomicInteger count = new AtomicInteger();
                    Assertions.assertEquals(SIZE / MODULO, readHeapService.read(offHeapRefs, _ -> count.incrementAndGet()));
                    Assertions.assertEquals(SIZE / MODULO, count.get());
                    readOffHeapMultiIndex.free(offHeapRefs);
                }
            }
        }
        long endIndex = System.nanoTime();
        System.out.println("find by multi index " + TimeUnit.NANOSECONDS.toMillis(endIndex - startIndex) +
                           " ms " + TimeUnit.NANOSECONDS.toMicros(endIndex - startIndex) / ((double) functionalKeys.length * loop) + "µs/search");
    }

    private static void findByIndex(int loop, FunctionalKey[] functionalKeys, OffHeapReadService readHeapService, OffHeapUniqueIndex offHeapUniqueIndex) {
        long startIndex = System.nanoTime();
        for (int i = 0; i < loop; i++) {
            for (FunctionalKey functionalKey : functionalKeys) {
                ReadOffHeapUniqueIndex readOffHeapIndex = readHeapService.getIndex(offHeapUniqueIndex);
                OffHeapRef offHeapRef = readOffHeapIndex.find(functionalKey);
                Assertions.assertNotNull(offHeapRef, functionalKey.toString());
                Assertions.assertTrue(offHeapRef.index() != -1, functionalKey.toString());
                Optional<Glob> data = readHeapService.read(offHeapRef);
                Assertions.assertTrue(data.isPresent());
                final Glob glob = data.get();
                Assertions.assertEquals(10 + (glob.get(DummyObject1.val1)) % 100, glob.get(DummyObject1.maxValue).intValue(), "at " + i);
                Assertions.assertEquals( "a name " + ((glob.get(DummyObject1.val1)) % MODULO), glob.get(DummyObject1.name));
            }
        }
        long endIndex = System.nanoTime();
        System.out.println("find by unique index " + TimeUnit.NANOSECONDS.toMillis(endIndex - startIndex) + " ms " +
                           TimeUnit.NANOSECONDS.toMicros(endIndex - startIndex) / ((double) loop * functionalKeys.length) + "µs/search");
    }

    private static void selectRead(OffHeapReadService readHeapService, List<Glob> globs) throws IOException {
        List<Glob> received = new ArrayList<>();
        final long startRead = System.currentTimeMillis();
        readHeapService.readAll(glob -> {
            received.add(glob);
        }, Set.of(DummyObject1.val1, DummyObject1.data1));
        System.out.println("Read " + (System.currentTimeMillis() - startRead) + " ms");

        Assertions.assertEquals(globs.size(), received.size());
        final Glob glob = received.get(0);
        final Integer val1 = glob.get(DummyObject1.val1);
        Assertions.assertEquals(10 + val1 % 100, glob.get(DummyObject1.data1).longValue());
    }

    private static void loopRead(OffHeapReadService readHeapService, List<Glob> globs) throws IOException {
        List<Glob> received = new ArrayList<>();
        final long startRead = System.currentTimeMillis();
        readHeapService.readAll(glob -> {
            received.add(glob);
        });
        System.out.println("Read " + (System.currentTimeMillis() - startRead) + " ms");

        Assertions.assertEquals(globs.size(), received.size());
        final Glob glob = received.get(0);
        final Integer i1 = glob.get(DummyObject1.val1);
        Assertions.assertArrayEquals(new int[]{i1, i1 + 1, i1 + 2}, glob.get(DummyObject1.data));
    }


    public static class DummyObject1 {
        public static final GlobType TYPE;

        public static final StringField name;

        public static final IntegerField val1;

        public static final IntegerField val2;

        public static final IntegerField maxValue;

        @ArraySize_(3)
        public static final IntegerArrayField data;

        public static final LongField data1;

        public static final DoubleField data2;

        public static final DateField data3;

        public static final DateTimeField data4;

        public static final BooleanField data5;

        public static final LongField data6;

        @MaxSize_(value = 13, allow_truncate = true)
        public static final StringField fixSizeStrAllowTruncate;

        @MaxSize_(value = 22, allow_truncate = false)
        public static final StringField fixSizeStrNoTruncate;

        static {
            final GlobTypeBuilder globTypeBuilder = GlobTypeBuilderFactory.create("DummyObject1");
            TYPE = globTypeBuilder.unCompleteType();
            name = globTypeBuilder.declareStringField("name");
            val1 = globTypeBuilder.declareIntegerField("val1");
            val2 = globTypeBuilder.declareIntegerField("val2");
            data = globTypeBuilder.declareIntegerArrayField("data", ArraySize.create(3));
            data1 = globTypeBuilder.declareLongField("data1");
            data2 = globTypeBuilder.declareDoubleField("data2");
            data3 = globTypeBuilder.declareDateField("data3");
            data4 = globTypeBuilder.declareDateTimeField("data4");
            data5 = globTypeBuilder.declareBooleanField("data5");
            data6 = globTypeBuilder.declareLongField("data6");
            maxValue = globTypeBuilder.declareIntegerField("maxValue");
            fixSizeStrAllowTruncate = globTypeBuilder.declareStringField("fixSizeStrAllowTruncate", MaxSize.create(13, true));
            fixSizeStrNoTruncate = globTypeBuilder.declareStringField("fixSizeStrNoTruncate", MaxSize.create(22, false));
            globTypeBuilder.complete();
        }
    }


    /*
    data :
      ordered list of index : permet de numeroté les instances de string avec leur offset dans le tableau. (Chunk de 1M?)
      nb chunk + size
    6 0 2 5
    XXZZTT
    ------
    3|2|10000
    1|2|5000

    index :
    Btree => 1|2 => index/index Leaf left/index Leaf Right

         10
       /   \
      3     14
     / \      \
    1   4      16

      3|10|14
 Addr 1|4||16

     */

}
