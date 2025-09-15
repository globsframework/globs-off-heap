package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.BooleanField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.MutableGlob;

public class Dummy3Type {
    public static final GlobType TYPE;

    public static final StringField data;

    public static final BooleanField unaligned;

    static {
        final GlobTypeBuilder globTypeBuilder = GlobTypeBuilderFactory.create("Dummy3Type");
        TYPE = globTypeBuilder.unCompleteType();
        data = globTypeBuilder.declareStringField("data");
        unaligned = globTypeBuilder.declareBooleanField("unaligned");
        globTypeBuilder.complete();
    }

    public static MutableGlob create(String data) {
        return TYPE.instantiate().set(Dummy3Type.data, data)
                .set(unaligned, true);
    }
}
