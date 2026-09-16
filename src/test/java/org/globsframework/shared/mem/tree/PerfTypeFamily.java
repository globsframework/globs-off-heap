package org.globsframework.shared.mem.tree;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.ArraySize;
import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.model.Heap7BitsString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
    private final FewKinds fewKinds;
    private KindSpread spread;

    /**
     * Two axes, deliberately crossed : how many fields a record has, and how many of them share a field
     * <em>kind</em>. A loop per kind amortizes one inlined accessor body over all the fields of that kind,
     * where an unrolled walk needs one body per field — so which of the two wins should follow the second
     * axis and not the first, and these four shapes are what tells them apart.
     * <p>
     * {@code narrow}/{@code wide} repeat the 15-kind block, so a field kind is used once or three times.
     * {@code *FewKinds} lay the same number of fields over three cheap kinds — int, long, double — so a kind
     * is used five or fifteen times at the same widths. {@code narrowFewKinds} is the discriminating one:
     * same field count as {@code narrow}, five times the fields per kind.
     * <p>
     * The few-kinds shapes read cheaper per field (no string, no zone id), so their absolute numbers are not
     * comparable with the others — only the ratio between the two readers within one shape is.
     */
    public enum Shape {
        narrow(1, false, 0),
        wide(3, false, 0),
        narrowFewKinds(1, true, 0),
        wideFewKinds(3, true, 0),
        // 45 fields, of which this many are fixed-size strings and the rest int/long/double : the axis that
        // actually decides, once fields-per-kind and field count have both been ruled out
        mixed6(3, true, 6),
        mixed12(3, true, 12),
        mixed18(3, true, 18),
        mixed24(3, true, 24),
        mixed36(3, true, 36),
        // 45 fields over K of the 15 kinds of the block, repeated : interpolates from the few-kind shapes to
        // wide, which is kinds15. The last axis left once width, per-field cost and fields-per-kind have all
        // failed to predict the sign.
        kinds4(3, 4),
        kinds6(3, 6),
        kinds9(3, 9),
        kinds12(3, 12),
        kinds15(3, 15),
        // wide, with its one 200-char fixed string brought down to the 40 of the other blocks : the only
        // difference left between wide and kinds15, and therefore the last thing that can explain why wide
        // is the one shape where the unrolled walk loses.
        wideSmallBig(3, false, 0);

        /** number of 15-field blocks in the record */
        public final int blocks;
        /** lay the fields over three cheap kinds instead of fifteen */
        public final boolean fewKinds;
        /** how many of those fields are a fixed-size string instead, i.e. have a big accessor body */
        public final int bigFields;
        /** how many distinct kinds of the block palette to spread the fields over, 0 for the other modes */
        public final int kinds;

        Shape(int blocks, boolean fewKinds, int bigFields) {
            this.blocks = blocks;
            this.fewKinds = fewKinds;
            this.bigFields = bigFields;
            this.kinds = 0;
        }

        Shape(int blocks, int kinds) {
            this.blocks = blocks;
            this.fewKinds = false;
            this.bigFields = 0;
            this.kinds = kinds;
        }
    }

    private PerfTypeFamily(GlobType type, Block[] blocks) {
        this.type = type;
        this.blocks = blocks;
        this.fewKinds = null;
        this.spread = null;
        this.key1 = blocks[0].val1;
        this.key2 = blocks[0].val2;
    }

    private PerfTypeFamily(GlobType type, FewKinds fewKinds) {
        this.type = type;
        this.blocks = null;
        this.spread = null;
        this.fewKinds = fewKinds;
        this.key1 = fewKinds.ints[0];
        this.key2 = fewKinds.ints[1];
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
        if (shape.kinds != 0) {
            return new PerfTypeFamily(builder,
                    KindSpread.declare(builder, shape.blocks * BLOCK_SIZE, shape.kinds));
        }
        if (shape.fewKinds) {
            return new PerfTypeFamily(builder,
                    FewKinds.declare(builder, shape.blocks * BLOCK_SIZE, shape.bigFields));
        }
        final Block[] blocks = new Block[shape.blocks];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = shape == Shape.wideSmallBig ? Block.declare(builder, i, 40)
                    : Block.declare(builder, i);
        }
        return new PerfTypeFamily(builder.build(), blocks);
    }

    private PerfTypeFamily(GlobTypeBuilder builder, FewKinds fewKinds) {
        this(builder.build(), fewKinds);
    }

    private PerfTypeFamily(GlobTypeBuilder builder, KindSpread spread) {
        this.type = builder.build();
        this.blocks = null;
        this.fewKinds = null;
        this.spread = spread;
        this.key1 = spread.key1;
        this.key2 = spread.key2;
    }

    public MutableGlob createData(int i, LocalDate localDate) {
        final MutableGlob glob = type.instantiate();
        if (spread != null) {
            spread.fill(glob, i, localDate);
            return glob;
        }
        if (fewKinds != null) {
            fewKinds.fill(glob, i);
            return glob;
        }
        for (int b = 0; b < blocks.length; b++) {
            blocks[b].fill(glob, i, b, localDate);
        }
        return glob;
    }

    /** fields per block, and the number of kinds a block uses : the two are the same by construction */
    private static final int BLOCK_SIZE = 15;

    /**
     * The same field count over three kinds : a third int, a third long, a third double. Cheap on purpose —
     * what is being compared is the walk, so the less each field costs, the more of the difference is the
     * walk itself.
     */
    private record FewKinds(IntegerField[] ints, LongField[] longs, DoubleField[] doubles,
                            StringField[] strings) {

        /**
         * @param bigFields how many fixed-size strings to declare instead of scalars. A fixed-size string is
         *                  the cheapest of the <em>big</em> accessors — a bulk copy and a String — so it is
         *                  what varies the amount of code an unrolled walk has to inline, at a constant
         *                  field count.
         */
        static FewKinds declare(GlobTypeBuilder builder, int fieldCount, int bigFields) {
            final int perKind = (fieldCount - bigFields) / 3;
            final IntegerField[] ints = new IntegerField[perKind];
            final LongField[] longs = new LongField[perKind];
            final DoubleField[] doubles = new DoubleField[perKind];
            final StringField[] strings = new StringField[bigFields];
            // the two index keys have to exist whatever the mix
            for (int i = 0; i < perKind; i++) {
                ints[i] = builder.declareIntegerField("int_" + i);
                longs[i] = builder.declareLongField("long_" + i);
                doubles[i] = builder.declareDoubleField("double_" + i);
            }
            for (int i = 0; i < bigFields; i++) {
                strings[i] = builder.declareStringField("str_" + i, MaxSize.create(22, false));
            }
            return new FewKinds(ints, longs, doubles, strings);
        }

        void fill(MutableGlob glob, int i) {
            for (int f = 0; f < ints.length; f++) {
                glob.set(ints[f], f == 0 ? i : f == 1 ? i % TestShared.MODULO : i + f);
                glob.set(longs[f], (long) i * (f + 1));
                glob.set(doubles[f], i + f / 10d);
            }
            for (int f = 0; f < strings.length; f++) {
                glob.set(strings[f], "value " + f + " " + (i % TestShared.MODULO));
            }
        }
    }

    /**
     * {@code fieldCount} fields laid over the first {@code kinds} kinds of the block palette, cycled. At
     * {@code kinds == 15} it is the same type as {@code wide}, which is what makes the series an
     * interpolation rather than a new family.
     */
    private static final class KindSpread {
        private final List<Consumer<MutableGlob>> setters = new ArrayList<>();
        private IntegerField key1;
        private IntegerField key2;

        static KindSpread declare(GlobTypeBuilder builder, int fieldCount, int kinds) {
            final KindSpread spread = new KindSpread();
            for (int f = 0; f < fieldCount; f++) {
                spread.declareOne(builder, f % kinds, f);
            }
            return spread;
        }

        private void declareOne(GlobTypeBuilder builder, int kind, int index) {
            final String s = "_" + index;
            switch (kind) {
                case 0 -> {
                    final IntegerField field = builder.declareIntegerField("i" + s);
                    if (key1 == null) {
                        key1 = field;
                    } else if (key2 == null) {
                        key2 = field;
                    }
                    setters.add(glob -> glob.set(field, key1 == field ? CURRENT.get()
                            : key2 == field ? CURRENT.get() % TestShared.MODULO : CURRENT.get() + index));
                }
                case 1 -> {
                    final LongField field = builder.declareLongField("l" + s);
                    setters.add(glob -> glob.set(field, (long) CURRENT.get() * (index + 1)));
                }
                case 2 -> {
                    final DoubleField field = builder.declareDoubleField("d" + s);
                    setters.add(glob -> glob.set(field, CURRENT.get() + index / 10d));
                }
                case 3 -> {
                    final StringField field = builder.declareStringField("s" + s);
                    setters.add(glob -> glob.set(field, "a name " + (CURRENT.get() % TestShared.MODULO)));
                }
                case 4 -> {
                    final BooleanField field = builder.declareBooleanField("b" + s);
                    setters.add(glob -> glob.set(field, CURRENT.get() % 2 == 0));
                }
                case 5 -> {
                    final IntegerArrayField field =
                            builder.declareIntegerArrayField("a" + s, ArraySize.create(3));
                    setters.add(glob -> glob.set(field, new int[]{CURRENT.get(), index, 2}));
                }
                case 6 -> {
                    final DateField field = builder.declareDateField("date" + s);
                    setters.add(glob -> glob.set(field, DATE.get()));
                }
                case 7 -> {
                    final DateTimeField field = builder.declareDateTimeField("dt" + s);
                    setters.add(glob -> glob.set(field, ZonedDateTime.of(DATE.get(),
                            LocalTime.of(CURRENT.get() % 24, index % 60, 1), ZoneId.systemDefault())));
                }
                case 8 -> {
                    final StringField field = builder.declareStringField("f7" + s, MaxSize.create(15),
                            Heap7BitsString.UNIQUE_GLOB);
                    setters.add(glob -> glob.set(field, "afds" + CURRENT.get()));
                }
                case 9 -> {
                    final StringField field =
                            builder.declareStringField("ft" + s, MaxSize.create(13, true));
                    setters.add(glob -> glob.set(field, "truncate" + CURRENT.get()));
                }
                case 10 -> {
                    final StringField field =
                            builder.declareStringField("fn" + s, MaxSize.create(22, false));
                    setters.add(glob -> glob.set(field, "no" + (CURRENT.get() % 100)));
                }
                case 11 -> {
                    final StringField field =
                            builder.declareStringField("fb" + s, MaxSize.create(40, false));
                    setters.add(glob -> glob.set(field, "a".repeat(30)));
                }
                case 12 -> {
                    final IntegerField field = builder.declareIntegerField("i2" + s);
                    setters.add(glob -> glob.set(field, 10 + CURRENT.get() % 100));
                }
                case 13 -> {
                    final LongField field = builder.declareLongField("l2" + s);
                    setters.add(glob -> glob.set(field, (long) CURRENT.get() * CURRENT.get()));
                }
                default -> {
                    final DoubleField field = builder.declareDoubleField("d2" + s);
                    setters.add(glob -> glob.set(field, 10d + CURRENT.get() % 100));
                }
            }
        }

        // the setters are closures built once, so the row they write travels beside them
        private static final ThreadLocal<Integer> CURRENT = new ThreadLocal<>();
        private static final ThreadLocal<LocalDate> DATE = new ThreadLocal<>();

        void fill(MutableGlob glob, int i, LocalDate localDate) {
            CURRENT.set(i);
            DATE.set(localDate);
            for (Consumer<MutableGlob> setter : setters) {
                setter.accept(glob);
            }
        }
    }

    /**
     * The field set of TestShared.DummyObject1, declared under a suffix so it can be repeated in one type :
     * every field kind the off-heap layout supports, including the three flavours of fixed-size string.
     */
    private record Block(StringField name, IntegerField val1, IntegerField val2, IntegerField maxValue,
                         IntegerArrayField data, LongField data1, DoubleField data2, DateField data3,
                         DateTimeField data4, BooleanField data5, LongField data6,
                         StringField fix7Bits, StringField fixTruncate, StringField fixNoTruncate,
                         StringField fixBig, int bigSize) {

        static Block declare(GlobTypeBuilder builder, int b) {
            return declare(builder, b, b == 0 ? 200 : 40);
        }

        /**
         * @param bigSize the width of the one big fixed-size string. Only the first block carries the 200 of
         *                the {@code wide} shape : three of them would make the record mostly one field, and
         *                the point of that shape is field count, not record size.
         */
        static Block declare(GlobTypeBuilder builder, int b, int bigSize) {
            final String s = "_" + b;
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
                    builder.declareStringField("fixBig" + s, MaxSize.create(bigSize, false)),
                    bigSize);
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
                    // as wide as the field allows, so the shape that narrows the field narrows the data
                    .set(fixBig, "a".repeat(bigSize - 10));
        }
    }
}
