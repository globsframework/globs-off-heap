package org.globsframework.shared.mem.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.Key;
import org.globsframework.core.model.KeyBuilder;

public class Heap7BitsString {
    public static final GlobType TYPE;

    public static final Glob UNIQUE_GLOB;

    public static final Key UNIQUE_KEY;

    static {
        final GlobTypeBuilder heapGlobTypeBuilder = GlobTypeBuilderFactory.create("HeapInline");
        TYPE = heapGlobTypeBuilder.build();
        UNIQUE_KEY = KeyBuilder.newEmptyKey(TYPE);
        UNIQUE_GLOB = TYPE.instantiate();
    }
}
