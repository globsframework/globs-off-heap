package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.ArraySize;
import org.globsframework.core.metamodel.annotations.ArraySize_;
import org.globsframework.core.metamodel.fields.IntegerArrayField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TestShared {
//    public static final int SIZE = 10_000_000;
//    public static final int MODULO = 1_000_000;
    public static final int SIZE = 500_000;
    public static final int MODULO = 50_000;

    @Test
    public void buildApi() throws IOException {

        List<Glob> globs = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) {
            globs.add(DummyObject1.TYPE.instantiate()
                    .set(DummyObject1.val1, i)
                    .set(DummyObject1.val2, (i % MODULO))
                    .set(DummyObject1.name, "a name " + (i % MODULO))
                    .set(DummyObject1.data1, 10 + i % 100)
                    .set(DummyObject1.data2, 10 + i % 100)
                    .set(DummyObject1.data3, 10 + i % 100)
                    .set(DummyObject1.data4, 10 + i % 100)
                    .set(DummyObject1.maxValue, 10 + i % 100)
                    .set(DummyObject1.data, new int[]{i, i+1, i+2})
            )
            ;
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
                offHeapService.createWrite(path, arena);

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

        List<Glob> received = new ArrayList<>();
        final long startRead = System.currentTimeMillis();
        readHeapService.readAll(glob -> {
            received.add(glob);
        });
        System.out.println("Read " + (System.currentTimeMillis() - startRead) + " ms");

        Assert.assertEquals(globs.size(), received.size());
        final Glob glob = received.get(0);
        final Integer i1 = glob.get(DummyObject1.val1);
        Assert.assertArrayEquals(new int[]{i1, i1 + 1, i1 + 2}, glob.get(DummyObject1.data));
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

        {
            long startIndex = System.nanoTime();
            for (int i = 0; i < loop; i++) {
                for (FunctionalKey functionalKey : functionalKeys) {
                    ReadOffHeapUniqueIndex readOffHeapIndex = readHeapService.getIndex(offHeapUniqueIndex);
                    OffHeapRef offHeapRef = readOffHeapIndex.find(functionalKey);
                    Assert.assertTrue(offHeapRef.index() != -1);
                    Assert.assertNotNull(offHeapRef);
                    Optional<Glob> data = readHeapService.read(offHeapRef);
                    Assert.assertTrue(data.isPresent());
//
//                Assert.assertEquals("at " + i, 10 + i % 100, data.get().get(DummyObject1.maxValue).intValue());
//                Assert.assertEquals( "a name " + (i % MODULO), data.get().get(DummyObject1.name));
                }
            }
            long endIndex = System.nanoTime();
            System.out.println("find by unique index " + TimeUnit.NANOSECONDS.toMillis(endIndex - startIndex) + " ms " +
                               TimeUnit.NANOSECONDS.toMicros(endIndex - startIndex) / ((double) loop * functionalKeys.length) + "µs/search");

        }

        {
            ReadOffHeapMultiIndex readOffHeapMultiIndex = readHeapService.getIndex(offHeapMultiIndex);
            System.out.println("start multi");
            long startIndex = System.nanoTime();
            for (int i = 0; i < loop; i++) {
                {
                    for (FunctionalKey functionalKey : functionalKeys) {
                        final OffHeapRefs offHeapRefs = readOffHeapMultiIndex.find(functionalKey);
                        Assert.assertNotNull(offHeapRefs);
                        AtomicInteger count = new AtomicInteger();
                        readHeapService.read(offHeapRefs, _ -> count.incrementAndGet());
                        Assert.assertEquals(SIZE / MODULO, count.get());
                        readOffHeapMultiIndex.free(offHeapRefs);
                    }
                }
            }
            long endIndex = System.nanoTime();
            System.out.println("find by multi index " + TimeUnit.NANOSECONDS.toMillis(endIndex - startIndex) +
                               " ms " + TimeUnit.NANOSECONDS.toMicros(endIndex - startIndex) / ((double) functionalKeys.length * loop) + "µs/search");
        }
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

        public static final LongField data2;

        public static final LongField data3;

        public static final LongField data4;

        static {
            final GlobTypeBuilder globTypeBuilder = GlobTypeBuilderFactory.create("DummyObject1");
            TYPE = globTypeBuilder.unCompleteType();
            name = globTypeBuilder.declareStringField("name");
            val1 = globTypeBuilder.declareIntegerField("val1");
            val2 = globTypeBuilder.declareIntegerField("val2");
            data = globTypeBuilder.declareIntegerArrayField("data", ArraySize.create(3));
            data1 = globTypeBuilder.declareLongField("data1");
            data2 = globTypeBuilder.declareLongField("data2");
            data3 = globTypeBuilder.declareLongField("data3");
            data4 = globTypeBuilder.declareLongField("data4");
            maxValue = globTypeBuilder.declareIntegerField("maxValue");
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
