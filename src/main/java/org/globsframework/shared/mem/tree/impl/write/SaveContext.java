package org.globsframework.shared.mem.tree.impl.write;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.OffHeapTypeInfoAccessor;
import org.globsframework.shared.mem.tree.impl.StringAddrAccessor;

import java.util.IdentityHashMap;
import java.util.Map;

public record SaveContext(Map<GlobType, IdentityHashMap<Glob, Long>> offsets, OffHeapTypeInfoAccessor offHeapTypeInfoMap, StringAddrAccessor stringAddrAccessor,
                          FreeOffset freeOffset, Flush flush) {
    public long getOffset(GlobType targetType, Glob glob) {
        return offsets.get(targetType).get(glob);
    }
}
