package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.GlobInstantiator;
import org.globsframework.shared.mem.DefaultOffHeapReadDataService;
import org.globsframework.shared.mem.hash.OffHeapHashAccess;
import org.globsframework.shared.mem.hash.OffHeapReadHashService;
import org.globsframework.shared.mem.hash.OffHeapUpdater;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;

import java.lang.foreign.Arena;
import java.nio.file.Path;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.function.Predicate;

class OffHeapReadHashServiceImpl implements OffHeapReadHashService {
    private final Path directory;
    private final Arena arena;
    private final GlobInstantiator globInstantiator;
    private final List<OffHeapHashServiceImpl.HashIndex> index;
    private final GlobType type;
    private final HashSet<GlobType> typeToSave;
    private final Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap;
    private final DefaultOffHeapReadDataService readDataService;

    public OffHeapReadHashServiceImpl(Path directory, Arena arena, GlobInstantiator globInstantiator,
                                      List<OffHeapHashServiceImpl.HashIndex> index, GlobType type,
                                      HashSet<GlobType> typeToSave, Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap) {
        this.directory = directory;
        this.arena = arena;
        this.globInstantiator = globInstantiator;
        this.index = index;
        this.type = type;
        this.typeToSave = typeToSave;
        this.offHeapTypeInfoMap = offHeapTypeInfoMap;
        readDataService = new DefaultOffHeapReadDataService(directory, arena, type, offHeapTypeInfoMap, typeToSave, globInstantiator);
    }

    @Override
    public OffHeapUpdater getUpdater() {
        return null;
    }

    @Override
    public OffHeapHashAccess getReader(String name) {
        OffHeapHashServiceImpl.HashIndex hashIndex = getHashIndex(name);

        HashReadIndex hashReadIndex = new HashReadIndex(hashIndex.size(), directory.resolve(name));
        return new OffHeapHashAccess() {
            @Override
            public Glob get(FunctionalKey key) {
                return hashReadIndex.getAt(key, readDataService);
            }
        };
    }

    private OffHeapHashServiceImpl.HashIndex getHashIndex(String name) {
        for (OffHeapHashServiceImpl.HashIndex hashIndex : this.index) {
            if (hashIndex.name().equals(name)) {
                return hashIndex;
            }
        }
        throw new InvalidParameterException("Hash index " + name + " not found");
    }
}
