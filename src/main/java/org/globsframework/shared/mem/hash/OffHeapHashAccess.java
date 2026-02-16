package org.globsframework.shared.mem.hash;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.DataConsumer;

import java.io.IOException;


public interface OffHeapHashAccess {
    Glob get(FunctionalKey key);

    void readAll(DataConsumer consumer) throws IOException;

}
