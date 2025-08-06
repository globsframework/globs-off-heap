package org.globsframework.shared.mem;

import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public interface OffHeapReadService extends AutoCloseable {
    ReadOffHeapUniqueIndex getIndex(OffHeapUniqueIndex index);

    ReadOffHeapMultiIndex getIndex(OffHeapNotUniqueIndex index);

    void readAll(DataConsumer consumer) throws IOException;

    void readAll(DataConsumer consumer, Set<Field> onlyFields) throws IOException;

    Optional<Glob> read(OffHeapRef offHeapRef);

    void read(OffHeapRefs offHeapRef, DataConsumer consumer);

    interface DataConsumer {
        void accept(Glob glob);
    }

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
