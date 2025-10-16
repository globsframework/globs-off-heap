package org.globsframework.shared.mem.impl;

import org.globsframework.shared.mem.tree.impl.OffHeapGlobTypeGroupLayoutImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OffHeapGlobTypeGroupLayoutTest {

    @Test
    void name() {
        final OffHeapGlobTypeGroupLayoutImpl offHeapGlobTypeGroupLayout = OffHeapGlobTypeGroupLayoutImpl.create(Dummy1Type.TYPE);
        Assertions.assertDoesNotThrow(() ->  offHeapGlobTypeGroupLayout.getGroupLayout(Dummy1Type.TYPE));
        Assertions.assertDoesNotThrow(() ->  offHeapGlobTypeGroupLayout.getGroupLayout(Dummy2Type.TYPE));
        Assertions.assertDoesNotThrow(() ->  offHeapGlobTypeGroupLayout.getGroupLayout(Dummy3Type.TYPE));
    }

}