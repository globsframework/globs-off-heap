package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.GlobInstantiator;
import org.globsframework.shared.mem.DefaultOffHeapReadDataService;
import org.globsframework.shared.mem.OffHeapReadDataService;
import org.globsframework.shared.mem.tree.*;
import org.globsframework.shared.mem.tree.impl.Index;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class DefaultOffHeapReadService implements OffHeapReadTreeService {
    private final Map<String, ReadIndex> indexMap;
    private final OffHeapReadDataService readDataService;

    public DefaultOffHeapReadService(Path directory, Arena arena, GlobType mainDataType, Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap,
                                     Set<GlobType> typesToSave, Map<String, Index> index, GlobInstantiator globInstantiator) throws IOException {
        readDataService = new DefaultOffHeapReadDataService(directory, arena, mainDataType, offHeapTypeInfoMap::get, typesToSave, globInstantiator);
        this.indexMap = new HashMap<>();
        for (Map.Entry<String, Index> entry : index.entrySet()) {
            if (entry.getValue().isUnique()) {
                indexMap.put(entry.getKey(), new DefaultReadOffHeapUniqueIndex(directory, (OffHeapUniqueIndex) entry.getValue(), readDataService));
            } else {
                indexMap.put(entry.getKey(), new DefaultReadOffHeapManyIndex(directory, (OffHeapNotUniqueIndex) entry.getValue(), readDataService));
            }
        }
    }

    public ReadOffHeapUniqueIndex getIndex(OffHeapUniqueIndex index) {
        return (ReadOffHeapUniqueIndex) indexMap.get(index.getName());
    }

    public ReadOffHeapMultiIndex getIndex(OffHeapNotUniqueIndex index) {
        return (ReadOffHeapMultiIndex) indexMap.get(index.getName());
    }

    public int read(OffHeapRefs offHeapRef, DataConsumer consumer) {
        return readDataService.read(offHeapRef.offset().getOffset(), consumer);
    }

    public void warmup() {
        readDataService.warmup();
    }

    public void readAll(DataConsumer consumer) throws IOException {
        readDataService.readAll(consumer);
    }


    public void readAll(DataConsumer consumer, Predicate<Field> onlyFields) throws IOException {
        readDataService.readAll(consumer, onlyFields);
    }

    public Glob read(OffHeapRef offHeapRef) {
        if (offHeapRef == null || offHeapRef.offset() == -1) {
            return null;
        }
        return readDataService.read(offHeapRef.offset());
    }

    public void close() throws IOException {
        for (ReadIndex value : indexMap.values()) {
            try {
                ((AutoCloseable) value).close();
            } catch (Exception e) {
            }
        }
        readDataService.close();
    }
}
