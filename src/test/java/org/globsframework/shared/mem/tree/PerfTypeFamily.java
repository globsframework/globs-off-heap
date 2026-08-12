package org.globsframework.shared.mem.tree;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.ArraySize;
import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.model.Heap7BitsString;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Several GlobTypes of the same shape, built at runtime, for the read benchmarks.
 * <p>
 * A benchmark reading a single type says less than it looks : the HandleAccess[] loop is already megamorphic
 * with one type (a type has fields of every kind), but everything downstream of it is not — one Glob class,
 * one set accessor instance per field, one HandleAccess instance per kind. Reading four *different* types at
 * the same call sites is what a process holding a few off-heap stores actually does, and it is what
 * globs-grpc's GeneratedGlobPerfTest does for the same reason.
 * <p>
 * {@link Shape} is the second axis : one block is the 15 fields of {@code TestShared.DummyObject1} (which is
 * what the numbers in CLAUDE.md were measured on), three blocks is a 45-field record. Width matters because
 * the per-field cost is what the optimisations target, and because a Glob of 45 fields is a different
 * DefaultGlob implementation than one of 15.
 */
public class PerfTypeFamily {
    public final GlobType type;
    public final IntegerField key1;
    public final IntegerField key2;
    private final Block[] blocks;

    /** number of 15-field blocks in the record */
    public enum Shape {
        narrow(1),
        wide(3);

        public final int blocks;

        Shape(int blocks) {
            this.blocks = blocks;
        }
    }

    private PerfTypeFamily(GlobType type, Block[] blocks) {
        this.type = type;
        this.blocks = blocks;
        this.key1 = blocks[0].val1;
        this.key2 = blocks[0].val2;
    }

    public static PerfTypeFamily[] create(Shape shape, int count) {
        final PerfTypeFamily[] families = new PerfTypeFamily[count];
        for (int i = 0; i < count; i++) {
            families[i] = create(shape, "PerfType" + shape.name() + i);
        }
        return families;
    }

    public static PerfTypeFamily create(Shape shape, String typeName) {
        final GlobTypeBuilder builder = GlobTypeBuilderFactory.create(typeName);
        final Block[] blocks = new Block[shape.blocks];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = Block.declare(builder, i);
        }
        return new PerfTypeFamily(builder.build(), blocks);
    }

    public MutableGlob createData(int i, LocalDate localDate) {
        final MutableGlob glob = type.instantiate();
        for (int b = 0; b < blocks.length; b++) {
            blocks[b].fill(glob, i, b, localDate);
        }
        return glob;
    }

    /**
     * The field set of TestShared.DummyObject1, declared under a suffix so it can be repeated in one type :
     * every field kind the off-heap layout supports, including the three flavours of fixed-size string.
     */
    private record Block(StringField name, IntegerField val1, IntegerField val2, IntegerField maxValue,
                         IntegerArrayField data, LongField data1, DoubleField data2, DateField data3,
                         DateTimeField data4, BooleanField data5, LongField data6,
                         StringField fix7Bits, StringField fixTruncate, StringField fixNoTruncate,
                         StringField fixBig) {

        static Block declare(GlobTypeBuilder builder, int b) {
            final String s = "_" + b;
            // only the first block carries the 200-char string : three of them would make the record mostly
            // one field, and the point of the wide shape is field count, not record size.
            final int bigSize = b == 0 ? 200 : 40;
            return new Block(
                    builder.declareStringField("name" + s),
                    builder.declareIntegerField("val1" + s),
                    builder.declareIntegerField("val2" + s),
                    builder.declareIntegerField("maxValue" + s),
                    builder.declareIntegerArrayField("data" + s, ArraySize.create(3)),
                    builder.declareLongField("data1" + s),
                    builder.declareDoubleField("data2" + s),
                    builder.declareDateField("data3" + s),
                    builder.declareDateTimeField("data4" + s),
                    builder.declareBooleanField("data5" + s),
                    builder.declareLongField("data6" + s),
                    builder.declareStringField("fix7Bits" + s, MaxSize.create(15), Heap7BitsString.UNIQUE_GLOB),
                    builder.declareStringField("fixTruncate" + s, MaxSize.create(13, true)),
                    builder.declareStringField("fixNoTruncate" + s, MaxSize.create(22, false)),
                    builder.declareStringField("fixBig" + s, MaxSize.create(bigSize, false)));
        }

        void fill(MutableGlob glob, int i, int b, LocalDate localDate) {
            glob.set(name, "a name " + b + " " + (i % TestShared.MODULO))
                    .set(val1, i)
                    .set(val2, i % TestShared.MODULO)
                    .set(maxValue, 10 + i % 100)
                    .set(data, new int[]{i, i + 1, i + 2})
                    .set(data1, 10L + i % 100)
                    .set(data2, 10d + i % 100)
                    .set(data3, localDate)
                    .set(data4, ZonedDateTime.of(localDate, LocalTime.of(i % 24, i % 60, 1 % 60), ZoneId.systemDefault()))
                    .set(data5, i % 2 == 0)
                    .set(data6, (long) i * i)
                    .set(fix7Bits, "afds" + i)
                    .set(fixTruncate, "⓿ﴴzdfeéd32" + i)
                    .set(fixNoTruncate, "揿꼴" + (i % 10))
                    .set(fixBig, "a".repeat(b == 0 ? 190 : 30));
        }
    }
}
