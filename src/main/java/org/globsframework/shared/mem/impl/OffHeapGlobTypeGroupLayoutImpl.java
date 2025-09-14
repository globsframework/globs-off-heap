package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;

import java.lang.foreign.GroupLayout;
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
        Map<GlobType, GroupLayout> offHeapTypeInfoMap = new HashMap<>();
        Set<GlobType> stack = new HashSet<>();

        final GroupLayout groupLayout = getGroupLayout(type, stack, new MyGroupLayoutAccessor(stack, offHeapTypeInfoMap));
        offHeapTypeInfoMap.put(type, groupLayout);
        return new OffHeapGlobTypeGroupLayoutImpl(offHeapTypeInfoMap);
    }

    private static GroupLayout getGroupLayout(GlobType type, Set<GlobType> stack, GroupLayoutFieldVisitor.GroupLayoutAccessor layoutAccessor) {
        stack.add(type);
        try {
            final GroupLayoutFieldVisitor groupLayoutAbstractFieldVisitor = new GroupLayoutFieldVisitor(layoutAccessor);
            for (Field field : type.getFields()) {
                field.safeAccept(groupLayoutAbstractFieldVisitor);
            }
            return groupLayoutAbstractFieldVisitor.createGroupLayout();
        } finally {
            stack.remove(type);
        }
    }

    private static class MyGroupLayoutAccessor implements GroupLayoutFieldVisitor.GroupLayoutAccessor {
        private final Set<GlobType> stack;
        private final Map<GlobType, GroupLayout> offHeapTypeInfoMap;

        public MyGroupLayoutAccessor(Set<GlobType> stack, Map<GlobType, GroupLayout> offHeapTypeInfoMap) {
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
                groupLayout = getGroupLayout(type, stack, this);
                offHeapTypeInfoMap.put(type, groupLayout);
            }
            return groupLayout;
        }
    }
}
