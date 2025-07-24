package org.globsframework.shared.mem;

import org.globsframework.core.model.Glob;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

public interface OffHeapReadService {
    ReadOffHeapIndex getIndex(OffHeapIndex index);

    void readAll(Consumer<Glob> consumer) throws IOException;

    Optional<Glob> read(OffHeapRef offHeapRef);
}
