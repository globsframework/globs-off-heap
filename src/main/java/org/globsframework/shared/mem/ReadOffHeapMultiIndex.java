package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKey;

public interface ReadOffHeapMultiIndex {
    OffHeapRefs find(FunctionalKey offHeapIndex);
}
