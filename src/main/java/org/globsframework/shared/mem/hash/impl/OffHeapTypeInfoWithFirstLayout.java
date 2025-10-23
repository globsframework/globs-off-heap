package org.globsframework.shared.mem.hash.impl;

import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;

import java.lang.invoke.VarHandle;

public class OffHeapTypeInfoWithFirstLayout {
    public final VarHandle isFreeHandle;
    public final OffHeapTypeInfo offHeapTypeInfo;

    public OffHeapTypeInfoWithFirstLayout(VarHandle isFreeHandle, OffHeapTypeInfo offHeapTypeInfo) {
        this.isFreeHandle = isFreeHandle;
        this.offHeapTypeInfo = offHeapTypeInfo;
    }
}
