package org.globsframework.shared.mem;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;

public interface OffHeapTypeInfoAccessor {
    OffHeapTypeInfo get(GlobType globType);
}
