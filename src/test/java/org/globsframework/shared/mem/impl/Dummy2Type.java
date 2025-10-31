package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.GlobArrayField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.model.HeapInline;
import org.globsframework.shared.mem.model.HeapMaxElement;

public class Dummy2Type {
    public static final GlobType TYPE;

    public static final StringField name;

    public static final GlobArrayField subObjectsInline;

    public static final GlobArrayField subObjects;

    public static final IntegerField aligned;

    static {
        final GlobTypeBuilder globTypeBuilder = GlobTypeBuilderFactory.create("Dummy2Type");
        TYPE = globTypeBuilder.unCompleteType();
        name = globTypeBuilder.declareStringField("name");
        subObjectsInline = globTypeBuilder.declareGlobArrayField("subObjectsInline", Dummy3Type.TYPE,
                HeapInline.UNIQUE_GLOB, HeapMaxElement.create(3));
        subObjects = globTypeBuilder.declareGlobArrayField("subObjects", Dummy3Type.TYPE,
                HeapMaxElement.create(3));
        aligned = globTypeBuilder.declareIntegerField("aligned");
        globTypeBuilder.complete();
    }

    public static MutableGlob create(String name) {
        return TYPE.instantiate().set(Dummy2Type.name, name)
                .set(subObjectsInline, new Glob[]{Dummy3Type.create("inline data3")})
                .set(subObjects, new Glob[]{Dummy3Type.create("data3")})
                .set(aligned, 12);
    }
}
