package org.globsframework.shared.mem.impl.write;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.impl.StringAddrAccessor;

import java.util.IdentityHashMap;
import java.util.Map;

public record SaveContext(Map<GlobType, IdentityHashMap<Glob, Long>> offsets, Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap, StringAddrAccessor stringAddrAccessor,
                          FreeOffset freeOffset, Flush flush) {
    public long getOffset(GlobType targetType, Glob glob) {
        return offsets.get(targetType).get(glob);
    }
}
