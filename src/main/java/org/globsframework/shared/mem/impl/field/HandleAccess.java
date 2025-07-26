package org.globsframework.shared.mem.impl.field;

import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.impl.read.ReadContext;
import org.globsframework.shared.mem.impl.write.SaveContext;

import java.lang.foreign.MemorySegment;

public interface HandleAccess {
    void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext);

    void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext);

}
