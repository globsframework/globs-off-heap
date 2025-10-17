package org.globsframework.shared.mem.tree;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.model.Glob;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class TestOnEmpty {
    @Test
    public void testFindOnEmpty() throws IOException {
        final FunctionalKeyBuilder uniqueFunctionalKeyBuilder =
                FunctionalKeyBuilderFactory.create(TestShared.DummyObject1.TYPE)
                        .add(TestShared.DummyObject1.val2)
                        .create();

        final FunctionalKeyBuilder multiFunctionalKeyBuilder =
                FunctionalKeyBuilderFactory.create(TestShared.DummyObject1.TYPE)
                        .add(TestShared.DummyObject1.name)
                        .create();


        OffHeapTreeService offHeapService = OffHeapTreeService.create(TestShared.DummyObject1.TYPE);
        OffHeapUniqueIndex offHeapUniqueIndex = offHeapService.declareUniqueIndex("uniqueIndex", uniqueFunctionalKeyBuilder);
        OffHeapNotUniqueIndex offHeapMultiIndex = offHeapService.declareNotUniqueIndex("multiIndex", multiFunctionalKeyBuilder);


        Arena arena = Arena.ofShared();

        final Path path = Path.of("/tmp/testEmpty");
        Files.createDirectories(path);
        OffHeapWriteTreeService offHeapWriteService =
                offHeapService.createWrite(path);
        offHeapWriteService.save(List.of());
        offHeapWriteService.close();
        OffHeapReadTreeService readHeapService = offHeapService.createRead(
                Path.of("/tmp/testEmpty"), arena);
        readHeapService.readAll(new DataConsumer() {
            @Override
            public void accept(Glob glob) {
                Assertions.fail("Should not be called");
            }
        });

        final ReadOffHeapUniqueIndex index = readHeapService.getIndex(offHeapUniqueIndex);
        final OffHeapRef offHeapRef = index.find(uniqueFunctionalKeyBuilder.create().set(TestShared.DummyObject1.val2, 1).create());
        Assertions.assertNotNull(offHeapRef);
        final Glob read = readHeapService.read(offHeapRef);
        Assertions.assertNull(read);

        final ReadOffHeapMultiIndex multiIndex = readHeapService.getIndex(offHeapMultiIndex);
        final OffHeapRefs offHeapRefs = multiIndex.find(multiFunctionalKeyBuilder.create().set(TestShared.DummyObject1.name, "a name 1").create());
        Assertions.assertNotNull(offHeapRefs);
        Assertions.assertEquals(0, offHeapRefs.size());
        readHeapService.read(offHeapRefs, glob -> {
            Assertions.fail("Should not be called");
        });
    }
}
