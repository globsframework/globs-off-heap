package org.globsframework.shared.mem.hash;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.GlobInstantiator;
import org.globsframework.shared.mem.hash.impl.OffHeapHashServiceImpl;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Path;

public interface OffHeapHashService {

    static OffHeapHashService create(GlobType globType) throws IOException {
        return new OffHeapHashServiceImpl(globType);
    }

    void declare(String name, FunctionalKeyBuilder keyBuilder, int size);

    OffHeapWriteHashService createWriter(Path directory);

    OffHeapReadHashService createReader(Path directory, Arena arena, GlobInstantiator globInstantiator);

    OffHeapUpdaterService createUpdater(Path directory, Arena arena);

}
