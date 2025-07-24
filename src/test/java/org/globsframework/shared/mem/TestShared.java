package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;
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

public class TestShared {

    public static final int SIZE = 12_000_000;

    @Test
    public void buildApi() throws IOException {

        List<Glob> globs = new ArrayList<Glob>();
        for (int i = 0; i < SIZE; i++) {
            globs.add(DummyObject1.TYPE.instantiate()
                    .set(DummyObject1.val1, i)
                    .set(DummyObject1.val2, (i % 10))
                    .set(DummyObject1.name, "a name " + (i % 10))
                    .set(DummyObject1.maxValue, 10 + i % 100));
        }


        final FunctionalKeyBuilderFactory functionalKeyBuilderFactory = FunctionalKeyBuilderFactory.create(DummyObject1.TYPE);
        final FunctionalKeyBuilder functionalKeyBuilder =
                functionalKeyBuilderFactory
//                        .add(DummyObject1.val2)
                        .add(DummyObject1.val1)
                        .create();


        OffHeapService offHeapService = OffHeapService.create(DummyObject1.TYPE);
        OffHeapIndex offHeapIndex = offHeapService.declareIndex("index1", functionalKeyBuilder);


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


        OffHeapReadService readHeapService = offHeapService.createRead(
                Path.of("/tmp/test"), arena);

        List<Glob> received = new ArrayList<>();
        final long startRead = System.currentTimeMillis();
        readHeapService.readAll(glob -> {
            received.add(glob);
        });
        System.out.println("Read " + (System.currentTimeMillis() - startRead) + " ms");

        Assert.assertEquals(globs.size(), received.size());


        long startIndex = System.currentTimeMillis();
        for (int i = 0; i < SIZE; i++) {
            final FunctionalKey functionalKey = functionalKeyBuilder.create()
                    .set(DummyObject1.val1, i)
//                    .set(DummyObject1.val2, (i % 10))
                    .create();
            ReadOffHeapIndex readOffHeapIndex = readHeapService.getIndex(offHeapIndex);
            OffHeapRef offHeapRef = readOffHeapIndex.find(functionalKey);
            Optional<Glob> data = readHeapService.read(offHeapRef);
            Assert.assertTrue(data.isPresent());
            Assert.assertEquals("at " + i, 10 + i % 100, data.get().get(DummyObject1.maxValue).intValue());
            Assert.assertEquals( "a name " + (i % 10), data.get().get(DummyObject1.name));
        }
        long endIndex = System.currentTimeMillis();
        System.out.println("find by index " + (endIndex - startIndex) + " ms");
    }


    public static class DummyObject1 {
        public static final GlobType TYPE;

        public static final StringField name;

        public static final IntegerField val1;

        public static final IntegerField val2;

        public static final IntegerField maxValue;

        static {
            final GlobTypeBuilder globTypeBuilder = GlobTypeBuilderFactory.create("DummyObject1");
            TYPE = globTypeBuilder.unCompleteType();
            name = globTypeBuilder.declareStringField("name");
            val1 = globTypeBuilder.declareIntegerField("val1");
            val2 = globTypeBuilder.declareIntegerField("val2");
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
