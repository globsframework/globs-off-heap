package org.globsframework.shared.mem.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.Key;
import org.globsframework.core.model.KeyBuilder;

/**
 * On a GlobType : read a record of this type with the per-kind loops of {@code PartitionedGlobReader}, never
 * with the class globs-generate would emit for it. One reader serves every whole-record read of the type, so
 * this covers {@code readAll} and the single reads alike.
 * <p>
 * The generated walk is the default when something is installed to generate, because it is faster on most
 * types — measured between +2 % and +31 % on ten of the twelve shapes swept in {@code ReadAllPerf}. On the other
 * two it is 9 % to 15 % slower, and <b>which of the two a type falls into cannot be predicted from its
 * shape</b>: fields per kind, field count, how much big-bodied accessor code it holds, how many distinct
 * kinds it uses and the width of its largest field were each measured as a candidate rule and each refuted.
 * Two 45-field types over the same fifteen kinds, differing by three fields, land 20 points apart. It is a
 * JIT outcome, not a property of the type.
 * <p>
 * So this annotation is not a hint to be guessed at: it is what a type says once someone has <em>measured</em>
 * it and found the loops faster. Put it on a type only with a benchmark behind it, the shape of
 * {@code ReadAllPerf} — and measure both of its passes, since this reader serves every whole-record read and
 * the two do not always agree: on the {@code wide} shape the generated walk loses 9 to 10 % over ten thousand
 * records and wins 11 % over one.
 * <p>
 * It changes nothing where nothing generates: {@code OffHeapTypeInfo.reader} is then the partitioned reader
 * for every type anyway.
 */
public class HeapLoopRead {
    public static final GlobType TYPE;

    public static final Glob UNIQUE_GLOB;

    public static final Key UNIQUE_KEY;

    static {
        final GlobTypeBuilder heapGlobTypeBuilder = GlobTypeBuilderFactory.create("HeapLoopRead");
        TYPE = heapGlobTypeBuilder.build();
        UNIQUE_KEY = KeyBuilder.newEmptyKey(TYPE);
        UNIQUE_GLOB = TYPE.instantiate();
    }
}
