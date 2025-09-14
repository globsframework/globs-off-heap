package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.GlobField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.shared.mem.model.HeapInline;

public class Dummy1Type {
    public static final GlobType TYPE;

    public static final IntegerField id;

    public static final GlobField subObjectInline;

    public static final GlobField subObject;

    static {
        final GlobTypeBuilder globTypeBuilder = GlobTypeBuilderFactory.create("Dummy1Type");
        TYPE = globTypeBuilder.unCompleteType();
        id = globTypeBuilder.declareIntegerField("id");
        subObjectInline = globTypeBuilder.declareGlobField("subObjectInline", Dummy2Type.TYPE, HeapInline.UNIQUE_GLOB);
        subObject = globTypeBuilder.declareGlobField("subObject", Dummy2Type.TYPE);
        globTypeBuilder.complete();
    }
}
