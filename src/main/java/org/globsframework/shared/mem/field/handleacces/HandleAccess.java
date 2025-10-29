package org.globsframework.shared.mem.field.handleacces;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.tree.impl.read.ReadContext;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;

import java.lang.foreign.MemorySegment;

public interface HandleAccess {

    Field getField();

    void save(Glob data, MemorySegment memorySegment, long offset, SaveContext saveContext);

    void readAtOffset(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext);

    default void scanOffset(MemorySegment memorySegment, long offset, ReferenceOffset referenceOffset){
    }

    public interface ReferenceOffset {
        void onRef(GlobType type, long offset);
    }
}
