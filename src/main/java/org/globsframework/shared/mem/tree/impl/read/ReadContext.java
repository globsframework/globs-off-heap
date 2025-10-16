package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.tree.impl.StringAccessorByAddress;
import org.globsframework.shared.mem.field.handleacces.HandleAccess;

import java.util.Map;

public record ReadContext(StringAccessorByAddress stringAccessorByAddress,
                          Map<GlobType, SegmentPerGlobType> perGlobTypeMap,
                          org.globsframework.core.model.GlobInstantiator globInstantiator) {
    public Glob read(GlobType targetType, long dataOffset) {
        final SegmentPerGlobType segmentPerGlobType = perGlobTypeMap.get(targetType);
        if (segmentPerGlobType == null) {
            throw new RuntimeException("Bug " + targetType.getName() + " not found");
        }
        final MutableGlob instantiate = globInstantiator.newGlob(targetType);
        for (HandleAccess handleAccess : segmentPerGlobType.offHeapTypeInfo().handleAccesses) {
            handleAccess.readAtOffset(instantiate, segmentPerGlobType.segment(), dataOffset, this);
        }
        return instantiate;
    }
}
