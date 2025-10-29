package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.GlobInstantiator;
import org.globsframework.shared.mem.OffsetHeader;
import org.globsframework.shared.mem.hash.OffHeapHashService;
import org.globsframework.shared.mem.hash.OffHeapReadHashService;
import org.globsframework.shared.mem.hash.OffHeapUpdaterService;
import org.globsframework.shared.mem.hash.OffHeapWriteHashService;
import org.globsframework.shared.mem.tree.impl.OffHeapGlobTypeGroupLayoutImpl;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.RootOffHeapTypeInfo;

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
    public static final String FREE_ID = "__FreeId__";
    public static final int HEADER_SIZE = 8;
    private final GlobType type;
    private final List<HashIndex> index =  new ArrayList<HashIndex>();
    private final HashSet<GlobType> typeToSave;
    private final Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap = new HashMap<>();
    private final OffsetHeader offsetHeader = globType -> HEADER_SIZE; // offset for next free position (aligned)

    public OffHeapHashServiceImpl(GlobType type) {
        typeToSave = new HashSet<>();
        this.type = type;
        final Set<GlobType> visited = new HashSet<>();
        extractTypeWithVarSize(type, typeToSave, visited);
        typeToSave.add(type);
        ValueLayout valueLayout = ValueLayout.JAVA_LONG.withName(FREE_ID);
        final ValueLayout[] withFirstLayout = {valueLayout};
        for (GlobType globType : visited) {
            final OffHeapGlobTypeGroupLayoutImpl offHeapGlobTypeGroupLayout = OffHeapGlobTypeGroupLayoutImpl.create(globType, withFirstLayout);
            final GroupLayout primaryGroupLayout = offHeapGlobTypeGroupLayout.getPrimaryGroupLayout();
            final HashMap<GlobType, OffHeapTypeInfo> inline = new HashMap<>();
            for (GlobType inlineType : offHeapGlobTypeGroupLayout.inlineType()) {
                inline.put(inlineType, OffHeapTypeInfo.create(inlineType, offHeapGlobTypeGroupLayout.getGroupLayoutForInline(inlineType)));
            }
            RootOffHeapTypeInfo offHeapTypeInfo = new RootOffHeapTypeInfo(OffHeapTypeInfo.create(globType, offHeapGlobTypeGroupLayout.getPrimaryGroupLayout()), inline);
            final VarHandle varHandle = primaryGroupLayout.varHandle(MemoryLayout.PathElement.groupElement(FREE_ID));
            offHeapTypeInfoMap.put(globType,
                    new OffHeapTypeInfoWithFirstLayout(varHandle, offHeapTypeInfo));
        }
    }

    @Override
    public void declare(String name, FunctionalKeyBuilder keyBuilder, int size) {
        index.add(new HashIndex(name, keyBuilder, tableSizeFor(size)));
    }

    @Override
    public OffHeapWriteHashService createWriter(Path directory) {
        return new OffHeapWriteHashServiceImpl(directory, type, typeToSave, offHeapTypeInfoMap, index, offsetHeader);
    }

    @Override
    public OffHeapReadHashService createReader(Path directory, Arena arena, GlobInstantiator globInstantiator) {
        return new OffHeapReadHashServiceImpl(directory, arena, globInstantiator, index, type, typeToSave, offHeapTypeInfoMap, offsetHeader);
    }

    @Override
    public OffHeapUpdaterService createUpdater(Path directory, Arena arena) {
        return new OffHeapUpdaterServiceImpl(directory, arena, index, type, typeToSave, offHeapTypeInfoMap, offsetHeader);
    }
}
