package org.globsframework.shared.mem.impl;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.shared.mem.*;
import org.globsframework.shared.mem.impl.read.DefaultOffHeapReadService;
import org.globsframework.shared.mem.impl.write.DefaultOffHeapWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.*;
import java.nio.file.Path;
import java.util.*;

public class DefaultOffHeapService implements OffHeapService {
    private static final Logger log = LoggerFactory.getLogger(DefaultOffHeapService.class);
    public static final String STRING_SUFFIX_ADDR = "_addr_";
    public static final String STRING_SUFFIX_LEN = "_len_";
    public static final String DATE_TIME_DATE_SUFFIX = "_date_";
    public static final String DATE_TIME_TIME_SUFFIX = "_time_";
    public static final String DATE_TIME_NANO_SUFFIX = "_nano_";
    public static final String DATE_TIME_ZONE_ID_SUFFIX = "_zoneId_";
    public static final int DATE_TIME_MAX_ZONE_ID_SIZE = 52; // to be align with 8 Bytes
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
    public OffHeapWriteService createWrite(Path directory, Arena arena) throws IOException {
        return new DefaultOffHeapWriteService(directory, arena, offHeapTypeInfo, index);
    }

    @Override
    public OffHeapReadService createRead(Path directory, Arena arena) throws IOException {
        return new DefaultOffHeapReadService(directory, arena, offHeapTypeInfo, index);
    }

    public static String getIndexNameFile(String indexName) {
        return indexName + "Unique.data";
    }

    public static String getIndexDataNameFile(String indexName) {
        return indexName + "Many.data";
    }

}
