package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.core.model.MutableGlob;

import java.lang.foreign.MemorySegment;

/**
 * Fills one Glob from one record. Owns the walk over the fields of a type, so that the call sites that read
 * a record — five in DefaultOffHeapReadDataService, two in the inline HandleAccess — do not each write their
 * own loop over {@code HandleAccess[]}, and so that the shape of that walk can be changed in one place.
 */
public interface GlobReader {

    void read(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext);
}
