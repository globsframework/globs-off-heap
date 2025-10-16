package org.globsframework.shared.mem.tree;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.GlobInstantiator;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Path;

public interface OffHeapTreeService {
    static OffHeapTreeService create(GlobType type) {
        return new DefaultOffHeapTreeService(type);
    }

    OffHeapUniqueIndex declareUniqueIndex(String name, FunctionalKeyBuilder functionalKeyBuilder);

    OffHeapNotUniqueIndex declareNotUniqueIndex(String name, FunctionalKeyBuilder functionalKeyBuilder);

    OffHeapWriteTreeService createWrite(Path directory) throws IOException;

    default OffHeapReadTreeService createRead(Path directory, Arena arena) throws IOException {
        return createRead(directory, arena, GlobType::instantiate);
    }

    OffHeapReadTreeService createRead(Path directory, Arena arena, GlobInstantiator globInstantiator) throws IOException;
}
