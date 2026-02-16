package org.globsframework.shared.mem;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.tree.impl.StringAccessorByAddress;
import org.globsframework.shared.mem.tree.impl.read.TypeSegment;

import java.io.IOException;
import java.util.function.Predicate;

public interface OffHeapReadDataService extends StringAccessorByAddress {
    Glob read(long offset);

    Glob read(long offset, Predicate<Field> onlyFields);

    int read(long[] offset, DataConsumer consumer);

    void readAll(DataConsumer consumer) throws IOException;

    void readAll(DataConsumer consumer, Predicate<Field> onlyFields) throws IOException;

    void warmup(DataConsumer dataConsumer);

    void close();

    TypeSegment getSegment(GlobType globType);
}
