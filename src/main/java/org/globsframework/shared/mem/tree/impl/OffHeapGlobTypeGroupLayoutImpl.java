package org.globsframework.shared.mem.tree.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class OffHeapGlobTypeGroupLayoutImpl implements OffHeapGlobTypeGroupLayout {
    public static final ValueLayout[] EMPTY_LAYOUTS = new ValueLayout[0];
    private final GroupLayout primaryGroupLayout;
    private final Map<GlobType, GroupLayout> offHeapTypeInfoMap;

    public OffHeapGlobTypeGroupLayoutImpl(GroupLayout primaryGroupLayout, Map<GlobType, GroupLayout> offHeapTypeInfoMap) {
        this.primaryGroupLayout = primaryGroupLayout;
        this.offHeapTypeInfoMap = offHeapTypeInfoMap;
    }

    public GroupLayout getGroupLayout(GlobType globType) {
        final GroupLayout groupLayout = offHeapTypeInfoMap.get(globType);
        if (groupLayout == null) {
            throw new RuntimeException("No group layout for " + globType.getName());
        }
        return groupLayout;
    }

    public GroupLayout getPrimaryGroupLayout() {
        return primaryGroupLayout;
    }

    public static OffHeapGlobTypeGroupLayoutImpl create(GlobType type) {
        return create(type, EMPTY_LAYOUTS);
    }

    public static OffHeapGlobTypeGroupLayoutImpl create(GlobType type, ValueLayout[] withFirstLayout) {
        Map<GlobType, GroupLayout> offHeapTypeInfoMap = new HashMap<>();
        Set<GlobType> stack = new HashSet<>();

        final GroupLayout groupLayout = getGroupLayout(type, stack, new StackedGroupLayoutAccessor(stack, offHeapTypeInfoMap), withFirstLayout);
        offHeapTypeInfoMap.put(type, groupLayout);
        return new OffHeapGlobTypeGroupLayoutImpl(groupLayout, offHeapTypeInfoMap);
    }

    private static GroupLayout getGroupLayout(GlobType type, Set<GlobType> stack, GroupLayoutFieldVisitor.GroupLayoutAccessor layoutAccessor, ValueLayout[] withFirstLayouts) {
        stack.add(type);
        try {
            final GroupLayoutFieldVisitor groupLayoutAbstractFieldVisitor = new GroupLayoutFieldVisitor(layoutAccessor, withFirstLayouts);
            for (Field field : type.getFields()) {
                field.safeAccept(groupLayoutAbstractFieldVisitor);
            }
            return groupLayoutAbstractFieldVisitor.createGroupLayout();
        } finally {
            stack.remove(type);
        }
    }

    private static class StackedGroupLayoutAccessor implements GroupLayoutFieldVisitor.GroupLayoutAccessor {
        private final Set<GlobType> stack;
        private final Map<GlobType, GroupLayout> offHeapTypeInfoMap;

        public StackedGroupLayoutAccessor(Set<GlobType> stack, Map<GlobType, GroupLayout> offHeapTypeInfoMap) {
            this.stack = stack;
            this.offHeapTypeInfoMap = offHeapTypeInfoMap;
        }

        @Override
        public GroupLayout getLayout(GlobType type) {
            if (stack.contains(type)) {
                throw new RuntimeException("Cycle detected in type hierarchy for type " + type);
            }
            GroupLayout groupLayout = offHeapTypeInfoMap.get(type);
            if (groupLayout == null) {
                groupLayout = getGroupLayout(type, stack, this, EMPTY_LAYOUTS); // no additional layout for glob inlined in parent.
                offHeapTypeInfoMap.put(type, groupLayout);
            }
            return groupLayout;
        }
    }
}
