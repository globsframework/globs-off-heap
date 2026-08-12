package org.globsframework.shared.mem.tree;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.model.Glob;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.globsframework.shared.mem.tree.TestShared.MODULO;
import static org.globsframework.shared.mem.tree.TestShared.createData;

/*
FindPerf measures the index descent only -- find() stops at an offset. This one measures what an application
does with that offset : materializing the Glob, which is where the per-field work is.

  find        : the descent, same as FindPerf, for scale
  read        : one Glob out of a known offset -- the HandleAccess[] loop
  findAndRead : what a cache lookup actually costs end to end
  readAll     : the same loop amortized over every record, no index

Run :
  mvn -o test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
  java -cp target/classes:target/test-classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main ReadPerf
*/
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class ReadPerf {
    private static final int COUNT = 10_000;

    private ReadOffHeapUniqueIndex readOffHeapIndex;
    private OffHeapReadTreeService readHeapService;
    private FunctionalKey[] functionalKeys;
    private OffHeapRef[] refs;
    private int cursor;

    @Setup
    public void setUp() throws IOException {
        List<Glob> globs = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            final LocalDate localDate = LocalDate.of(1900 + (i % 200), i % 11 + 1, i % 28 + 1);
            globs.add(createData(i, localDate));
        }

        final FunctionalKeyBuilder uniqueFunctionalKeyBuilder =
                FunctionalKeyBuilderFactory.create(TestShared.DummyObject1.TYPE)
                        .add(TestShared.DummyObject1.val1)
                        .add(TestShared.DummyObject1.val2)
                        .create();

        OffHeapTreeService offHeapService = OffHeapTreeService.create(TestShared.DummyObject1.TYPE);
        OffHeapUniqueIndex offHeapUniqueIndex = offHeapService.declareUniqueIndex("uniqueIndex", uniqueFunctionalKeyBuilder);

        final Path path = Files.createTempDirectory("readperf");
        offHeapService.createWrite(path).save(globs);

        functionalKeys = new FunctionalKey[1000];
        for (int i = 0; i < functionalKeys.length; i++) {
            functionalKeys[i] = uniqueFunctionalKeyBuilder.create()
                    .set(TestShared.DummyObject1.val1, i)
                    .set(TestShared.DummyObject1.val2, (i % MODULO))
                    .create();
        }

        readHeapService = offHeapService.createRead(path, Arena.ofShared());
        readOffHeapIndex = readHeapService.getIndex(offHeapUniqueIndex);

        // the refs the index would hand back, resolved once so read() can be measured on its own
        refs = new OffHeapRef[functionalKeys.length];
        for (int i = 0; i < functionalKeys.length; i++) {
            refs[i] = readOffHeapIndex.find(functionalKeys[i]);
        }
    }

    /** a different key every call : one index descent is not one cached branch */
    private int next() {
        int i = cursor++;
        if (cursor == functionalKeys.length) {
            cursor = 0;
        }
        return i;
    }

    @Benchmark
    public OffHeapRef find() {
        return readOffHeapIndex.find(functionalKeys[next()]);
    }

    @Benchmark
    public Glob read() {
        return readHeapService.read(refs[next()]);
    }

    @Benchmark
    public Glob findAndRead() {
        return readHeapService.read(readOffHeapIndex.find(functionalKeys[next()]));
    }

    @Benchmark
    public void readAll(Blackhole blackhole) throws IOException {
        readHeapService.readAll(glob -> {
            blackhole.consume(glob);
            return true;
        });
    }
}
