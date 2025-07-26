package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.shared.mem.impl.DefaultOffHeapService;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Path;

public interface OffHeapService {
    static OffHeapService create(GlobType type) {
        return new DefaultOffHeapService(type);
    }

    OffHeapUniqueIndex declareUniqueIndex(String name, FunctionalKeyBuilder functionalKeyBuilder);

    OffHeapNotUniqueIndex declareNotUniqueIndex(String name, FunctionalKeyBuilder functionalKeyBuilder);

    OffHeapWriteService createWrite(Path file, Arena arena) throws IOException;

    OffHeapReadService createRead(Path file, Arena arena) throws IOException;
}
