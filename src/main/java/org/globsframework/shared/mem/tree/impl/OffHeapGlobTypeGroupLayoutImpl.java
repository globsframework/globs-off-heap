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
    private final Map<GlobType, GroupLayout> offHeapTypeInfoMap;

    public OffHeapGlobTypeGroupLayoutImpl(Map<GlobType, GroupLayout> offHeapTypeInfoMap) {
        this.offHeapTypeInfoMap = offHeapTypeInfoMap;
    }

    public GroupLayout getGroupLayout(GlobType globType) {
        final GroupLayout groupLayout = offHeapTypeInfoMap.get(globType);
        if (groupLayout == null) {
            throw new RuntimeException("No group layout for " + globType.getName());
        }
        return groupLayout;
    }

    public static OffHeapGlobTypeGroupLayoutImpl create(GlobType type) {
        return create(type, new ValueLayout[0]);
    }

    public static OffHeapGlobTypeGroupLayoutImpl create(GlobType type, ValueLayout[] withFirstLayout) {
        Map<GlobType, GroupLayout> offHeapTypeInfoMap = new HashMap<>();
        Set<GlobType> stack = new HashSet<>();

        final GroupLayout groupLayout = getGroupLayout(type, stack, new StackedGroupLayoutAccessor(stack, offHeapTypeInfoMap, withFirstLayout), withFirstLayout);
        offHeapTypeInfoMap.put(type, groupLayout);
        return new OffHeapGlobTypeGroupLayoutImpl(offHeapTypeInfoMap);
    }

    private static GroupLayout getGroupLayout(GlobType type, Set<GlobType> stack, GroupLayoutFieldVisitor.GroupLayoutAccessor layoutAccessor, ValueLayout[] withFirstBoolean) {
        stack.add(type);
        try {
            final GroupLayoutFieldVisitor groupLayoutAbstractFieldVisitor = new GroupLayoutFieldVisitor(layoutAccessor, withFirstBoolean);
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
        private final ValueLayout[] withFirstBoolean;

        public StackedGroupLayoutAccessor(Set<GlobType> stack, Map<GlobType, GroupLayout> offHeapTypeInfoMap, ValueLayout[] withFirstBoolean) {
            this.stack = stack;
            this.offHeapTypeInfoMap = offHeapTypeInfoMap;
            this.withFirstBoolean = withFirstBoolean;
        }

        @Override
        public GroupLayout getLayout(GlobType type) {
            if (stack.contains(type)) {
                throw new RuntimeException("Cycle detected in type hierarchy for type " + type);
            }
            GroupLayout groupLayout = offHeapTypeInfoMap.get(type);
            if (groupLayout == null) {
                groupLayout = getGroupLayout(type, stack, this, withFirstBoolean);
                offHeapTypeInfoMap.put(type, groupLayout);
            }
            return groupLayout;
        }
    }
}
