package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.GlobInstantiator;
import org.globsframework.shared.mem.hash.OffHeapHashService;
import org.globsframework.shared.mem.hash.OffHeapReadHashService;
import org.globsframework.shared.mem.hash.OffHeapWriteHashService;
import org.globsframework.shared.mem.tree.impl.OffHeapGlobTypeGroupLayoutImpl;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;

import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;
import java.util.*;

import static org.globsframework.shared.mem.DataSaver.extractTypeWithVarSize;
import static org.globsframework.shared.mem.hash.impl.HashWriteIndex.tableSizeFor;

public class OffHeapHashServiceImpl implements OffHeapHashService {
    public static final String IS_FREE = "__isFree__";
    private final GlobType type;
    private List<HashIndex> index =  new ArrayList<HashIndex>();
    private final HashSet<GlobType> typeToSave;
    private final Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap = new HashMap<>();

    public OffHeapHashServiceImpl(GlobType type) {
        typeToSave = new HashSet<>();
        this.type = type;
        final Set<GlobType> visited = new HashSet<>();
        extractTypeWithVarSize(type, typeToSave, visited);
        typeToSave.add(type);
        ValueLayout valueLayout = ValueLayout.JAVA_BOOLEAN.withName(IS_FREE);
        final ValueLayout[] withFirstLayout = {valueLayout};
        for (GlobType globType : visited) {
            final OffHeapGlobTypeGroupLayoutImpl offHeapGlobTypeGroupLayout = OffHeapGlobTypeGroupLayoutImpl.create(globType, withFirstLayout);
            final GroupLayout primaryGroupLayout = offHeapGlobTypeGroupLayout.getPrimaryGroupLayout();
            final VarHandle varHandle = primaryGroupLayout.varHandle(MemoryLayout.PathElement.groupElement(IS_FREE));
            offHeapTypeInfoMap.put(globType,
                    new OffHeapTypeInfoWithFirstLayout(varHandle, OffHeapTypeInfo.create(globType, offHeapGlobTypeGroupLayout)));
        }
    }

    record HashIndex(String name, FunctionalKeyBuilder keyBuilder, int size) {
    }

    @Override
    public void declare(String name, FunctionalKeyBuilder keyBuilder, int size) {
        index.add(new HashIndex(name, keyBuilder, tableSizeFor(size)));
    }

    @Override
    public OffHeapWriteHashService createWriter(Path directory) {
        return new OffHeapWriteHashServiceImpl(directory, type, typeToSave, offHeapTypeInfoMap, index);
    }

    @Override
    public OffHeapReadHashService createReader(Path directory, Arena arena, GlobInstantiator globInstantiator) {
        return new OffHeapReadHashServiceImpl(directory, arena, globInstantiator, index, type, typeToSave, offHeapTypeInfoMap);
    }

}
