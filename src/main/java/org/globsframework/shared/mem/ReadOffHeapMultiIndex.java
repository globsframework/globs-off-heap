package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKey;

public interface ReadOffHeapMultiIndex {
    OffHeapRefs find(FunctionalKey offHeapIndex);

    OffHeapRefs search(FunctionalKey offHeapIndex);

    void free(OffHeapRefs offHeapRefs);
}
