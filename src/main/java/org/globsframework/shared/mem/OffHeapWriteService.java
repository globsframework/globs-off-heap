package org.globsframework.shared.mem;

import org.globsframework.core.model.Glob;

import java.io.IOException;
import java.util.Collection;

public interface OffHeapWriteService extends AutoCloseable {
    void save(Collection<Glob> globs) throws IOException;

    void close() throws IOException;
}
