package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.field.handleacces.*;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * The read walk, split by field kind.
 * <p>
 * A single loop over {@code HandleAccess[]} is one call site for the fourteen implementations, shared by every
 * type in the process : megamorphic, so {@code readAtOffset} is never inlined. One loop per kind over an array
 * typed to the concrete (and {@code final}) class is monomorphic instead, which lets the callee inline and the
 * offset add fold into the load.
 * <p>
 * The four Glob-valued kinds are left together in {@link #others} : they are rare, and each of them recurses
 * into another reader, which dwarfs one dispatch.
 * <p>
 * A field is set independently of the others, so splitting the walk does not change what is read — only the
 * order in which the record is touched.
 */
public final class PartitionedGlobReader implements GlobReader {
    private final BooleanFieldHandleAccess[] booleans;
    private final IntegerFieldHandleAccess[] integers;
    private final LongFieldHandleAccess[] longs;
    private final DoubleFieldHandleAccess[] doubles;
    private final DateFieldHandleAccess[] dates;
    private final DateTimeFieldHandleAccess[] dateTimes;
    private final StringFieldHandleAccess[] strings;
    private final FixSizeStringFieldHandleAccess[] fixSizeStrings;
    private final FixSize7BitsStringFieldHandleAccess[] fixSize7BitsStrings;
    private final HandleAccess[] others;

    private PartitionedGlobReader(BooleanFieldHandleAccess[] booleans, IntegerFieldHandleAccess[] integers,
                                  LongFieldHandleAccess[] longs, DoubleFieldHandleAccess[] doubles,
                                  DateFieldHandleAccess[] dates, DateTimeFieldHandleAccess[] dateTimes,
                                  StringFieldHandleAccess[] strings,
                                  FixSizeStringFieldHandleAccess[] fixSizeStrings,
                                  FixSize7BitsStringFieldHandleAccess[] fixSize7BitsStrings,
                                  HandleAccess[] others) {
        this.booleans = booleans;
        this.integers = integers;
        this.longs = longs;
        this.doubles = doubles;
        this.dates = dates;
        this.dateTimes = dateTimes;
        this.strings = strings;
        this.fixSizeStrings = fixSizeStrings;
        this.fixSize7BitsStrings = fixSize7BitsStrings;
        this.others = others;
    }

    public static GlobReader create(HandleAccess[] handleAccesses) {
        final List<BooleanFieldHandleAccess> booleans = new ArrayList<>();
        final List<IntegerFieldHandleAccess> integers = new ArrayList<>();
        final List<LongFieldHandleAccess> longs = new ArrayList<>();
        final List<DoubleFieldHandleAccess> doubles = new ArrayList<>();
        final List<DateFieldHandleAccess> dates = new ArrayList<>();
        final List<DateTimeFieldHandleAccess> dateTimes = new ArrayList<>();
        final List<StringFieldHandleAccess> strings = new ArrayList<>();
        final List<FixSizeStringFieldHandleAccess> fixSizeStrings = new ArrayList<>();
        final List<FixSize7BitsStringFieldHandleAccess> fixSize7BitsStrings = new ArrayList<>();
        final List<HandleAccess> others = new ArrayList<>();
        for (HandleAccess handleAccess : handleAccesses) {
            switch (handleAccess) {
                case BooleanFieldHandleAccess a -> booleans.add(a);
                case IntegerFieldHandleAccess a -> integers.add(a);
                case LongFieldHandleAccess a -> longs.add(a);
                case DoubleFieldHandleAccess a -> doubles.add(a);
                case DateFieldHandleAccess a -> dates.add(a);
                case DateTimeFieldHandleAccess a -> dateTimes.add(a);
                case StringFieldHandleAccess a -> strings.add(a);
                case FixSizeStringFieldHandleAccess a -> fixSizeStrings.add(a);
                case FixSize7BitsStringFieldHandleAccess a -> fixSize7BitsStrings.add(a);
                default -> others.add(handleAccess);
            }
        }
        return new PartitionedGlobReader(
                booleans.toArray(BooleanFieldHandleAccess[]::new),
                integers.toArray(IntegerFieldHandleAccess[]::new),
                longs.toArray(LongFieldHandleAccess[]::new),
                doubles.toArray(DoubleFieldHandleAccess[]::new),
                dates.toArray(DateFieldHandleAccess[]::new),
                dateTimes.toArray(DateTimeFieldHandleAccess[]::new),
                strings.toArray(StringFieldHandleAccess[]::new),
                fixSizeStrings.toArray(FixSizeStringFieldHandleAccess[]::new),
                fixSize7BitsStrings.toArray(FixSize7BitsStringFieldHandleAccess[]::new),
                others.toArray(HandleAccess[]::new));
    }

    @Override
    public void read(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        for (IntegerFieldHandleAccess access : integers) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
        for (LongFieldHandleAccess access : longs) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
        for (DoubleFieldHandleAccess access : doubles) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
        for (BooleanFieldHandleAccess access : booleans) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
        for (StringFieldHandleAccess access : strings) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
        for (FixSizeStringFieldHandleAccess access : fixSizeStrings) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
        for (FixSize7BitsStringFieldHandleAccess access : fixSize7BitsStrings) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
        for (DateFieldHandleAccess access : dates) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
        for (DateTimeFieldHandleAccess access : dateTimes) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
        for (HandleAccess access : others) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
    }
}
