package org.globsframework.shared.mem.impl;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.shared.mem.OffHeapNotUniqueIndex;
import org.globsframework.shared.mem.OffHeapUniqueIndex;

class DefaultOffHeapIndex implements OffHeapUniqueIndex, OffHeapNotUniqueIndex, Index {
    private final String name;
    private final FunctionalKeyBuilder functionalKeyBuilder;
    private final boolean isUnique;

    public DefaultOffHeapIndex(String name, FunctionalKeyBuilder functionalKeyBuilder, boolean isUnique) {
        this.name = name;
        this.functionalKeyBuilder = functionalKeyBuilder;
        this.isUnique = isUnique;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FunctionalKeyBuilder getKeyBuilder() {
        return functionalKeyBuilder;
    }

    @Override
    public boolean isUnique() {
        return isUnique;
    }
}
