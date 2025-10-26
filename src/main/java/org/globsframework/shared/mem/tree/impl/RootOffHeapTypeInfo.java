package org.globsframework.shared.mem.tree.impl;

import org.globsframework.core.metamodel.GlobType;

import java.util.Map;

public record RootOffHeapTypeInfo(OffHeapTypeInfo primary, Map<GlobType, OffHeapTypeInfo> inline) {
}
