package org.globsframework.shared.mem.tree;

import org.globsframework.core.functional.FunctionalKeyBuilder;

public interface OffHeapNotUniqueIndex {
    String getName();

    FunctionalKeyBuilder getKeyBuilder();
}
