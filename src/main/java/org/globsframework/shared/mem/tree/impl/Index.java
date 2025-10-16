package org.globsframework.shared.mem.tree.impl;

import org.globsframework.core.functional.FunctionalKeyBuilder;

public interface Index {
    String getName();
    FunctionalKeyBuilder getKeyBuilder();
    boolean isUnique();
}
