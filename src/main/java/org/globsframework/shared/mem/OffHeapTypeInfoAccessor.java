package org.globsframework.shared.mem;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.RootOffHeapTypeInfo;

public interface OffHeapTypeInfoAccessor {
    RootOffHeapTypeInfo get(GlobType globType);
}
