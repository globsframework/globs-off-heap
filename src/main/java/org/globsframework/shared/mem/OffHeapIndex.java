package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKeyBuilder;

public interface OffHeapIndex {
    String getName();
    FunctionalKeyBuilder getKeyBuilder();
}
