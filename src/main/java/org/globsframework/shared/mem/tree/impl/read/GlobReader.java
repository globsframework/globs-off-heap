package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.core.model.MutableGlob;

import java.lang.foreign.MemorySegment;

/**
 * Fills one Glob from one record.
 * <p>
 * <b>No state, ever.</b> One instance per GlobType serves the whole process — {@code OffHeapTypeInfo.reader}
 * and {@code walkReader} are public fields, read concurrently by every reader of a store, and the nested and
 * inline kinds call them from inside another read. An implementation that wants to carry something per
 * record (a cursor, a buffer) breaks every one of those call sites at once, so it passes what it needs
 * through the arguments instead.
 * <p>
 * Owns the walk over the fields of a type, so that the call sites that read a record — five in
 * DefaultOffHeapReadDataService, two in the inline HandleAccess — do not each write their own loop over
 * {@code HandleAccess[]}, and so that the shape of that walk can be changed in one place.
 */
public interface GlobReader {

    void read(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext);
}
