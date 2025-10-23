package org.globsframework.shared.mem.tree.impl;

import org.globsframework.core.metamodel.GlobType;

import java.lang.foreign.GroupLayout;

public interface OffHeapGlobTypeGroupLayout {
    GroupLayout getGroupLayout(GlobType globType);

    GroupLayout getPrimaryGroupLayout();
}
