package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKey;

public interface ReadOffHeapUniqueIndex {
    OffHeapRef find(FunctionalKey offHeapIndex);

    OffHeapRefs search(FunctionalKey offHeapIndex);

}
