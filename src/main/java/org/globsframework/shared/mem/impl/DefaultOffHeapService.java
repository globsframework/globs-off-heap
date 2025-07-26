package org.globsframework.shared.mem.impl;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.shared.mem.*;
import org.globsframework.shared.mem.impl.read.DefaultOffHeapReadService;
import org.globsframework.shared.mem.impl.read.ReadIndex;
import org.globsframework.shared.mem.impl.write.DefaultOffHeapWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.*;
import java.nio.file.Path;
import java.util.*;

public class DefaultOffHeapService implements OffHeapService {
    private static final Logger log = LoggerFactory.getLogger(DefaultOffHeapService.class);
    public static final String SUFFIX_ADDR = "_addr";
    public static final String SUFFIX_LEN = "_len";
    public static final String CONTENT_DATA = "content.data";
    public static final String STRINGS_DATA = "strings.data";
    private final OffHeapTypeInfo offHeapTypeInfo;
    private final Map<String, Index> index = new HashMap<>();

    public DefaultOffHeapService(GlobType type) {
        offHeapTypeInfo = new OffHeapTypeInfo(type);
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
    public OffHeapWriteService createWrite(Path file, Arena arena) throws IOException {
        return new DefaultOffHeapWriteService(file, arena, offHeapTypeInfo, index);
    }

    @Override
    public OffHeapReadService createRead(Path directory, Arena arena) throws IOException {
        return new DefaultOffHeapReadService(directory, arena, offHeapTypeInfo, index);
    }

    public static String getIndexNameFile(String indexName) {
        return indexName + ".data";
    }

    public static String getIndexDataNameFile(String indexName) {
        return indexName + "ManyRefs" + ".data";
    }

}
