package org.globsframework.shared.mem.hash;

import org.globsframework.core.model.Glob;

public interface OffHeapUpdater {
    int update(Glob data); // free slot still available
}
