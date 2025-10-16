package org.globsframework.shared.mem.tree.impl;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.GlobInstantiator;
import org.globsframework.shared.mem.DataSaver;
import org.globsframework.shared.mem.tree.impl.read.DefaultOffHeapReadService;
import org.globsframework.shared.mem.tree.impl.write.DefaultOffHeapWriteTreeService;
import org.globsframework.shared.mem.tree.*;

import java.io.IOException;
import java.lang.foreign.*;
import java.nio.file.Path;
import java.util.*;

public class DefaultOffHeapTreeService implements OffHeapTreeService {

    public static final String STRING_SUFFIX_ADDR = "_addr_";
    public static final String STRING_SUFFIX_LEN = "_len_";
    public static final String DATE_TIME_DATE_SUFFIX = "_date_";
    public static final String DATE_TIME_TIME_SUFFIX = "_time_";
    public static final String DATE_TIME_NANO_SUFFIX = "_nano_";
    public static final String DATE_TIME_ZONE_ID_SUFFIX = "_zoneId_";
    public static final int DATE_TIME_MAX_ZONE_ID_SIZE = 52; // to be align with 8 Bytes
    public static final String CONTENT_DATA = "content.data";
    public static final String STRINGS_DATA = "strings.data";
    public static final String GLOB_SET = "_set_";
    public static final String GLOB_LEN = "_len_";
    private final GlobType mainDataType;
    private final Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap = new HashMap<>();
    private final Map<String, Index> index = new HashMap<>();
    private final Set<GlobType> typeToSave;

    public DefaultOffHeapTreeService(GlobType type) {
        mainDataType = type;
        typeToSave = new HashSet<>();
        final HashSet<GlobType> visited = new HashSet<>();
        DataSaver.extractTypeWithVarSize(type, typeToSave, visited);
        typeToSave.add(type);
        for (GlobType globType : visited) {
            offHeapTypeInfoMap.put(globType, OffHeapTypeInfo.create(globType, OffHeapGlobTypeGroupLayoutImpl.create(globType)));
        }
    }

    public static String createContentFileName(GlobType mainDataType) {
        return CONTENT_DATA + "_" + mainDataType.getName();
    }

    @Override
    public OffHeapUniqueIndex declareUniqueIndex(String name, FunctionalKeyBuilder functionalKeyBuilder) {
        final DefaultOffHeapIndex value = new DefaultOffHeapIndex(name, functionalKeyBuilder, true);
        index.put(name, value);
        return value;
    }

    @Override
    public OffHeapNotUniqueIndex declareNotUniqueIndex(String name, FunctionalKeyBuilder functionalKeyBuilder) {
        final DefaultOffHeapIndex value = new DefaultOffHeapIndex(name, functionalKeyBuilder, false);
        index.put(name, value);
        return value;
    }

    @Override
    public OffHeapWriteTreeService createWrite(Path directory) throws IOException {
        return new DefaultOffHeapWriteTreeService(directory, mainDataType, offHeapTypeInfoMap, typeToSave, index);
    }

    @Override
    public OffHeapReadTreeService createRead(Path directory, Arena arena, GlobInstantiator globInstantiator) throws IOException {
        return new DefaultOffHeapReadService(directory, arena, mainDataType, offHeapTypeInfoMap, typeToSave, index, globInstantiator);
    }

    public static String getIndexNameFile(String indexName) {
        return indexName + "Unique.data";
    }

    public static String getIndexDataNameFile(String indexName) {
        return indexName + "Many.data";
    }

}
