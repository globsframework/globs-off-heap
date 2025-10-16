package org.globsframework.shared.mem.hash;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.model.GlobInstantiator;

import java.lang.foreign.Arena;
import java.nio.file.Path;

public interface OffHeapHashService {

    void declare(String name, FunctionalKeyBuilder keyBuilder, int size);

    OffHeapWriteHashService create(Path directory);

    OffHeapReadHashService create(Path directory, Arena arena, GlobInstantiator globInstantiator);

}
