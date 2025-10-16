package org.globsframework.shared.mem.tree;

import org.globsframework.core.functional.FunctionalKeyBuilder;

public interface OffHeapUniqueIndex {
    String getName();

    FunctionalKeyBuilder getKeyBuilder();
}
