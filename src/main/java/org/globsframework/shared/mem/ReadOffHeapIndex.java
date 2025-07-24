package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKey;

public interface ReadOffHeapIndex {
    OffHeapRef find(FunctionalKey offHeapIndex);
}
