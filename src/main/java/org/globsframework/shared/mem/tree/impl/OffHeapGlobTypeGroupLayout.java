package org.globsframework.shared.mem.tree.impl;

import org.globsframework.core.metamodel.GlobType;

import java.lang.foreign.GroupLayout;
import java.util.Collection;

public interface OffHeapGlobTypeGroupLayout {
    GroupLayout getGroupLayoutForInline(GlobType globType);

    GroupLayout getPrimaryGroupLayout();

    Collection<GlobType> inlineType();
}
