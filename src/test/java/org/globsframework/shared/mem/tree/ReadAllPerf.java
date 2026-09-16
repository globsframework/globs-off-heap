package org.globsframework.shared.mem.tree;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.caller.ToGlobCallerService;
import org.globsframework.model.generator.AsmCallerWriteGenerator;
import org.globsframework.model.generator.AsmCallerWriteGeneratorService;
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
What the generated walk is worth on readAll, against the PartitionedGlobReader loops.

  readAll : one op = one pass over the 10 000 records of one store, which is the only path wired to the
            generated caller (readAll without the onlyFields predicate).
  read    : the control. A single record still goes through the partitioned reader in both arms, so this
            number has to stay put -- if it moves, the arm is measuring something other than the walk.

Same axes as ReadPerf : four different GlobTypes in round-robin (PerfTypeFamily) and the narrow / wide
shapes, 15 and 45 fields. The `caller` param is the arm : JMH forks a JVM per parameter combination, so
the two arms never share a profile, and the property is installed before anything builds a reader.

Run :
  mvn -o test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
  java -cp target/classes:target/test-classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main ReadAllPerf
*/
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class ReadAllPerf {
    private static final int COUNT = 10_000;
    private static final int KEY_COUNT = 1000;
    private static final int FAMILY_COUNT = 4;

    /**
     * off : nothing installed to generate, the walk stays on PartitionedGlobReader. loop : the same walk as
     * the generated ones — wrappers, boxed offset, per-field unboxing — driven by core's plain loop over the
     * function array instead of an emitted method, which is what isolates the cost of unrolling from the
     * cost of carrying the arguments. The others are the generated walk, by how many entries globs-generate
     * puts in one emitted method — on being the single method it always emitted. An unrolled call is ~12 bytes, so a method holding more than ~27 of them is
     * past FreqInlineSize (325) and stops being inlined as a whole : that is what the chunks are chasing,
     * and why they can only matter on the wide shape (45 fields ~ 540 bytes) and not on the narrow one
     * (15 fields ~ 180, already under it, and where 16 and 24 therefore emit exactly what `on` emits).
     */
    public enum Caller {
        off(-1), on(0), loop(-1), chunk8(8), chunk12(12), chunk16(16), chunk24(24);

        public final int chunk;

        Caller(int chunk) {
            this.chunk = chunk;
        }
    }

    @Param({"narrow", "wide"})
    public PerfTypeFamily.Shape shape;

    @Param({"off", "on", "loop", "chunk8", "chunk12", "chunk16", "chunk24"})
    public Caller caller;

    private OffHeapReadTreeService[] readHeapServices;
    private OffHeapRef[][] refs;
    private int cursor;
    private int family;

    @Setup
    public void setUp() throws IOException {
        // before the first type info is built : that is where the walk reader is decided
        if (caller == Caller.off) {
            System.clearProperty("globs.caller.toGlob");
            System.clearProperty(AsmCallerWriteGenerator.CHUNK_PROPERTY);
        } else if (caller == Caller.loop) {
            // the whole CallerGlobReader path minus the unrolling : same wrappers, same boxed offset
            System.setProperty("globs.caller.toGlob", LoopToGlobCallerServiceForTest.class.getName());
            System.clearProperty(AsmCallerWriteGenerator.CHUNK_PROPERTY);
        } else {
            System.setProperty("globs.caller.toGlob", AsmCallerWriteGeneratorService.class.getName());
            System.setProperty(AsmCallerWriteGenerator.CHUNK_PROPERTY, Integer.toString(caller.chunk));
        }
        ToGlobCallerService.Builder.reset();

        final PerfTypeFamily[] families = PerfTypeFamily.create(shape, FAMILY_COUNT);
        readHeapServices = new OffHeapReadTreeService[FAMILY_COUNT];
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

            final Path path = Files.createTempDirectory("readallperf");
            offHeapService.createWrite(path).save(globs);

            readHeapServices[f] = offHeapService.createRead(path, arena);
            final ReadOffHeapUniqueIndex index = readHeapServices[f].getIndex(offHeapUniqueIndex);

            // the refs the index would hand back, resolved once so read() can be measured on its own
            refs[f] = new OffHeapRef[KEY_COUNT];
            for (int i = 0; i < KEY_COUNT; i++) {
                refs[f][i] = index.find(uniqueFunctionalKeyBuilder.create()
                        .set(perfType.key1, i)
                        .set(perfType.key2, (i % MODULO))
                        .create());
            }
        }
    }

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
    public void readAll(Blackhole blackhole) throws IOException {
        readHeapServices[nextFamily()].readAll(glob -> {
            blackhole.consume(glob);
            return true;
        });
    }

    /** the control : one record, which the arm does not change */
    @Benchmark
    public Glob read() {
        final int f = nextFamily();
        return readHeapServices[f].read(refs[f][next()]);
    }
}
