package org.globsframework.shared.mem.hash.impl;

import org.globsframework.shared.mem.tree.impl.RootOffHeapTypeInfo;

import java.lang.invoke.VarHandle;

public class OffHeapTypeInfoWithFirstLayout {
    public final VarHandle isFreeHandle;
    public final RootOffHeapTypeInfo offHeapTypeInfo;

    public OffHeapTypeInfoWithFirstLayout(VarHandle isFreeHandle, RootOffHeapTypeInfo offHeapTypeInfo) {
        this.isFreeHandle = isFreeHandle;
        this.offHeapTypeInfo = offHeapTypeInfo;
    }
}
