package org.globsframework.shared.mem.tree;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

public interface OffHeapReadTreeService extends AutoCloseable {
    ReadOffHeapUniqueIndex getIndex(OffHeapUniqueIndex index);

    ReadOffHeapMultiIndex getIndex(OffHeapNotUniqueIndex index);

    void readAll(DataConsumer consumer) throws IOException;

    void readAll(DataConsumer consumer, Predicate<Field> onlyFields) throws IOException;

    Glob read(OffHeapRef offHeapRef);

    int read(OffHeapRefs offHeapRef, DataConsumer consumer);

    void warmup();

    default Collection<Glob> read(OffHeapRefs offHeapRef){
        List<Glob> globs = new ArrayList<>(offHeapRef.offset().size());
        read(offHeapRef, new DataConsumer() {
            @Override
            public void accept(Glob glob) {
                globs.add(glob);
            }
        });
        return globs;
    }

    void close() throws IOException;
}
