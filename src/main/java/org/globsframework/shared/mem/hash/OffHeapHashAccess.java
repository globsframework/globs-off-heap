package org.globsframework.shared.mem.hash;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.model.Glob;


public interface OffHeapHashAccess {
    Glob get(FunctionalKey key);
}
