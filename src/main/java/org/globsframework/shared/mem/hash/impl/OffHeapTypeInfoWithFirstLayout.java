package org.globsframework.shared.mem.hash.impl;

import org.globsframework.shared.mem.tree.impl.RootOffHeapTypeInfo;

import java.lang.invoke.VarHandle;

public class OffHeapTypeInfoWithFirstLayout {
    public final VarHandle freeIdHandle;
    public final RootOffHeapTypeInfo offHeapTypeInfo;

    public OffHeapTypeInfoWithFirstLayout(VarHandle freeIdHandle, RootOffHeapTypeInfo offHeapTypeInfo) {
        this.freeIdHandle = freeIdHandle;
        this.offHeapTypeInfo = offHeapTypeInfo;
    }
}
