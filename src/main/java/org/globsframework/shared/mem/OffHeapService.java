package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Path;

public interface OffHeapService {
    static OffHeapService create(GlobType type) {
        return new DefaultOffHeapService(type);
    }

    OffHeapIndex declareIndex(String name, FunctionalKeyBuilder functionalKeyBuilder);

    OffHeapWriteService createWrite(Path file, Arena arena) throws IOException;

    OffHeapReadService createRead(Path file, Arena arena) throws IOException;
}
