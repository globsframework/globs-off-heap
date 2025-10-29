package org.globsframework.shared.mem.tree.impl.write;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.StringAddrAccessor;

import java.util.IdentityHashMap;
import java.util.Map;

public record SaveContext(Map<GlobType, IdentityHashMap<Glob, Long>> offsets, OffHeapInlineTypeInfoAccessor offHeapTypeInfoMapForInline, StringAddrAccessor stringAddrAccessor) {
    public long getOffset(GlobType targetType, Glob glob) {
        return offsets.get(targetType).get(glob);
    }

    public interface OffHeapInlineTypeInfoAccessor {
        OffHeapTypeInfo get(GlobType type);
    }
}
