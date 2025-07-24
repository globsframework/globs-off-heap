package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.model.Glob;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.Collection;

public interface OffHeapWriteService {
    static OffHeapWriteService create(MemorySegment memorySegment) {
        return null;
    }

    OffHeapIndex declareIndex(FunctionalKeyBuilder functionalKeyBuilder);

    void save(Collection<Glob> globs) throws IOException;

    void close() throws IOException;
}
