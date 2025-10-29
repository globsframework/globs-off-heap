package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;

import java.util.function.Predicate;

public interface ReadContext {

    Glob read(long dataOffset);

    Glob read(long dataOffset, Predicate<Field> onlyFields);

    Glob read(GlobType targetType, long dataOffset);

    String get(int addr, int len);

    MutableGlob newGlob(GlobType targetType);

    OffHeapTypeInfo getOffHeapInlineTypeInfo(GlobType targetType);
}
