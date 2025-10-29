package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.GlobInstantiator;
import org.globsframework.shared.mem.DefaultOffHeapReadDataService;
import org.globsframework.shared.mem.OffsetHeader;
import org.globsframework.shared.mem.hash.OffHeapHashAccess;
import org.globsframework.shared.mem.hash.OffHeapReadHashService;

import java.lang.foreign.Arena;
import java.nio.file.Path;
import java.security.InvalidParameterException;
import java.util.*;

class OffHeapReadHashServiceImpl implements OffHeapReadHashService {
    private final Path directory;
    private final Arena arena;
    private final GlobInstantiator globInstantiator;
    private final List<HashIndex> index;
    private final GlobType type;
    private final HashSet<GlobType> typeToSave;
    private final Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap;
    private final OffsetHeader offsetHeader;
    private final DefaultOffHeapReadDataService readDataService;

    public OffHeapReadHashServiceImpl(Path directory, Arena arena, GlobInstantiator globInstantiator,
                                      List<HashIndex> index, GlobType type,
                                      HashSet<GlobType> typeToSave, Map<GlobType, OffHeapTypeInfoWithFirstLayout> offHeapTypeInfoMap, OffsetHeader offsetHeader) {
        this.directory = directory;
        this.arena = arena;
        this.globInstantiator = globInstantiator;
        this.index = index;
        this.type = type;
        this.typeToSave = typeToSave;
        this.offHeapTypeInfoMap = offHeapTypeInfoMap;
        this.offsetHeader = offsetHeader;
        readDataService =
                new DefaultOffHeapReadDataService(directory, arena, type,
                        globType -> offHeapTypeInfoMap.get(globType).offHeapTypeInfo,
                        typeToSave, globInstantiator, offsetHeader);
    }

    @Override
    public OffHeapHashAccess getReader(String name) {
        HashIndex hashIndex = getHashIndex(name);

        HashReadIndex hashReadIndex = new HashReadIndex(hashIndex, directory);
        return new OffHeapHashAccess() {
            @Override
            public Glob get(FunctionalKey key) {
                return hashReadIndex.getAt(key, readDataService);
            }
        };
    }

    private HashIndex getHashIndex(String name) {
        for (HashIndex hashIndex : this.index) {
            if (hashIndex.name().equals(name)) {
                return hashIndex;
            }
        }
        throw new InvalidParameterException("Hash index " + name + " not found");
    }
}
