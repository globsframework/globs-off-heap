package org.globsframework.shared.mem.tree;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.model.Glob;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.globsframework.shared.mem.tree.TestShared.MODULO;
import static org.globsframework.shared.mem.tree.TestShared.createData;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 2, time = 3)
@Fork(2)
@State(Scope.Thread)
public class FindPerf {
    private OffHeapTreeService offHeapService;
    private FunctionalKey[] functionalKeys;
    private ReadOffHeapUniqueIndex readOffHeapIndex;

    @Setup
    public void setUp() throws IOException {
        List<Glob> globs = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            final LocalDate localDate = LocalDate.of(1900 + (i % 200), i % 11 + 1, i % 28 + 1);
            globs.add(createData(i, localDate));
        }

        final FunctionalKeyBuilder uniqueFunctionalKeyBuilder =
                FunctionalKeyBuilderFactory.create(TestShared.DummyObject1.TYPE)
                        .add(TestShared.DummyObject1.val1)
                        .add(TestShared.DummyObject1.val2)
                        .create();

        offHeapService = OffHeapTreeService.create(TestShared.DummyObject1.TYPE);
        OffHeapUniqueIndex offHeapUniqueIndex = offHeapService.declareUniqueIndex("uniqueIndex", uniqueFunctionalKeyBuilder);

        Arena arena = Arena.ofShared();

        final Path path = Path.of("/tmp/test");
        Files.createDirectories(path);
        OffHeapWriteTreeService offHeapWriteService =
                offHeapService.createWrite(path);

        final long startWrite = System.currentTimeMillis();
        offHeapWriteService.save(globs);
        final long endWrite = System.currentTimeMillis();
        System.out.println("write " + (endWrite - startWrite) + " ms");

        functionalKeys = new FunctionalKey[1000];
        final FunctionalKeyBuilder optFunctionalKeyBuilder =
                FunctionalKeyBuilderFactory.create(TestShared.DummyObject1.TYPE)
                        .add(TestShared.DummyObject1.val1)
                        .add(TestShared.DummyObject1.val2)
                        .add(TestShared.DummyObject1.name)
                        .create();

        for (int i = 0; i < 1000; i++) {
            final FunctionalKey functionalKey = optFunctionalKeyBuilder.create()
                    .set(TestShared.DummyObject1.val1, i)
                    .set(TestShared.DummyObject1.val2, (i % MODULO))
                    .set(TestShared.DummyObject1.name, "a name " + (i % MODULO))
                    .create();
            functionalKeys[i] = functionalKey;
        }

        OffHeapReadTreeService readHeapService = offHeapService.createRead(
                Path.of("/tmp/test"), arena);


        readOffHeapIndex = readHeapService.getIndex(offHeapUniqueIndex);

    }

    @Benchmark
    public OffHeapRef findByIndex() {
        return readOffHeapIndex.find(functionalKeys[100]);
    }

}
