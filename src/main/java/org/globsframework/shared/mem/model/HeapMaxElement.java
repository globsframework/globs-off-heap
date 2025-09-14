package org.globsframework.shared.mem.model;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.Key;
import org.globsframework.core.model.KeyBuilder;

public class HeapMaxElement {
    public static final GlobType TYPE;

    public static final IntegerField maxSize;

    public static final Key UNIQUE_KEY;

    public static Glob create(int maxSize) {
        return TYPE.instantiate().set(HeapMaxElement.maxSize, maxSize);
    }

    static {
        final GlobTypeBuilder heapGlobTypeBuilder = GlobTypeBuilderFactory.create("HeapMaxElement");
        TYPE = heapGlobTypeBuilder.unCompleteType();
        maxSize = heapGlobTypeBuilder.declareIntegerField("maxSize");
        heapGlobTypeBuilder.complete();
        UNIQUE_KEY = KeyBuilder.newEmptyKey(TYPE);
    }
}
