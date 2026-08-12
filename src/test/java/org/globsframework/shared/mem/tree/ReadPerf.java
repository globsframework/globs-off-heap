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

/*
FindPerf measures the index descent only -- find() stops at an offset. This one measures what an application
does with that offset : materializing the Glob, which is where the per-field work is.

  find        : the descent, same as FindPerf, for scale
  read        : one Glob out of a known offset -- the HandleAccess[] loop
  findAndRead : what a cache lookup actually costs end to end
  readAll     : the same loop amortized over every record, no index

Every benchmark round-robins over FAMILY_COUNT *different* GlobTypes (see PerfTypeFamily) so the call sites
see what a process holding a few stores sees, and runs twice over the `shape` param : a 15-field record and a
45-field one. Numbers from the single-type version of this class are not comparable with these.

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
    private static final int KEY_COUNT = 1000;
    private static final int FAMILY_COUNT = 4;

    @Param({"narrow", "wide"})
    public PerfTypeFamily.Shape shape;

    private OffHeapReadTreeService[] readHeapServices;
    private ReadOffHeapUniqueIndex[] readOffHeapIndexes;
    private FunctionalKey[][] functionalKeys;
    private OffHeapRef[][] refs;
    private int cursor;
    private int family;

    @Setup
    public void setUp() throws IOException {
        final PerfTypeFamily[] families = PerfTypeFamily.create(shape, FAMILY_COUNT);
        readHeapServices = new OffHeapReadTreeService[FAMILY_COUNT];
        readOffHeapIndexes = new ReadOffHeapUniqueIndex[FAMILY_COUNT];
        functionalKeys = new FunctionalKey[FAMILY_COUNT][];
        refs = new OffHeapRef[FAMILY_COUNT][];

        final Arena arena = Arena.ofShared();
        for (int f = 0; f < FAMILY_COUNT; f++) {
            final PerfTypeFamily perfType = families[f];
            List<Glob> globs = new ArrayList<>();
            for (int i = 0; i < COUNT; i++) {
                final LocalDate localDate = LocalDate.of(1900 + (i % 200), i % 11 + 1, i % 28 + 1);
                globs.add(perfType.createData(i, localDate));
            }

            final FunctionalKeyBuilder uniqueFunctionalKeyBuilder =
                    FunctionalKeyBuilderFactory.create(perfType.type)
                            .add(perfType.key1)
                            .add(perfType.key2)
                            .create();

            OffHeapTreeService offHeapService = OffHeapTreeService.create(perfType.type);
            OffHeapUniqueIndex offHeapUniqueIndex = offHeapService.declareUniqueIndex("uniqueIndex", uniqueFunctionalKeyBuilder);

            final Path path = Files.createTempDirectory("readperf");
            offHeapService.createWrite(path).save(globs);

            functionalKeys[f] = new FunctionalKey[KEY_COUNT];
            for (int i = 0; i < KEY_COUNT; i++) {
                functionalKeys[f][i] = uniqueFunctionalKeyBuilder.create()
                        .set(perfType.key1, i)
                        .set(perfType.key2, (i % MODULO))
                        .create();
            }

            readHeapServices[f] = offHeapService.createRead(path, arena);
            readOffHeapIndexes[f] = readHeapServices[f].getIndex(offHeapUniqueIndex);

            // the refs the index would hand back, resolved once so read() can be measured on its own
            refs[f] = new OffHeapRef[KEY_COUNT];
            for (int i = 0; i < KEY_COUNT; i++) {
                refs[f][i] = readOffHeapIndexes[f].find(functionalKeys[f][i]);
            }
        }
    }

    /** a different key every call : one index descent is not one cached branch */
    private int next() {
        int i = cursor++;
        if (cursor == KEY_COUNT) {
            cursor = 0;
        }
        return i;
    }

    /** a different type every call : the read call sites are shared between the stores of a process */
    private int nextFamily() {
        int f = family++;
        if (family == FAMILY_COUNT) {
            family = 0;
        }
        return f;
    }

    @Benchmark
    public OffHeapRef find() {
        final int f = nextFamily();
        return readOffHeapIndexes[f].find(functionalKeys[f][next()]);
    }

    @Benchmark
    public Glob read() {
        final int f = nextFamily();
        return readHeapServices[f].read(refs[f][next()]);
    }

    @Benchmark
    public Glob findAndRead() {
        final int f = nextFamily();
        return readHeapServices[f].read(readOffHeapIndexes[f].find(functionalKeys[f][next()]));
    }

    @Benchmark
    public void readAll(Blackhole blackhole) throws IOException {
        readHeapServices[nextFamily()].readAll(glob -> {
            blackhole.consume(glob);
            return true;
        });
    }
}
