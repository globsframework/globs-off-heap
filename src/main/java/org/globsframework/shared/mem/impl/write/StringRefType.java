package org.globsframework.shared.mem.impl.write;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;

public class StringRefType {
    public static final GlobType TYPE;

    public static final IntegerField id;

    public static final IntegerField offset;

    public static final IntegerField len;

    static {
        final GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("StringRefType");
        TYPE = typeBuilder.unCompleteType();
        id = typeBuilder.declareIntegerField("id");
        offset = typeBuilder.declareIntegerField("offset");
        len = typeBuilder.declareIntegerField("len");
        typeBuilder.complete();
    }
}
