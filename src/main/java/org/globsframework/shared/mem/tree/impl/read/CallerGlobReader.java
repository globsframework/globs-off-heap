package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.caller.ToGlobCallerAll;
import org.globsframework.core.model.caller.ToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobFunction;
import org.globsframework.shared.mem.field.handleacces.GlobArrayHandleAccess;
import org.globsframework.shared.mem.field.handleacces.GlobArrayInlineHandleAccess;
import org.globsframework.shared.mem.field.handleacces.GlobHandleAccess;
import org.globsframework.shared.mem.field.handleacces.GlobInlineHandleAccess;
import org.globsframework.shared.mem.field.handleacces.HandleAccess;

import java.lang.foreign.MemorySegment;

/**
 * The read walk as a generated, unrolled caller : one call site per field, each with a {@code static final}
 * receiver, instead of the loops of {@link PartitionedGlobReader}.
 * <p>
 * {@link PartitionedGlobReader} removed the megamorphic dispatch of a single {@code HandleAccess[]} loop by
 * splitting it per field kind, which makes each loop monomorphic but still leaves one call site shared by
 * every type of the process and every field of a kind. A {@link ToGlobCallerAll} emitted by globs-generate
 * goes one step further : the array is unrolled into as many call sites as there are fields, each reading its
 * function from a {@code static final} of the generated class, so the receiver is a JIT constant there.
 * <p>
 * Only the whole-record walk of {@code readAll} is built this way for now — a class is emitted per type, which
 * is worth it for a pass over a file and not for a single record.
 * <p>
 * Built only when something is installed to generate ({@code -Dglobs.caller.toGlob=...}) :
 * {@link ToGlobCallerFactory#generated()} answers null otherwise, and the walk then stays on
 * {@link PartitionedGlobReader}. Core's looped {@code LoopToGlobCallerFactory} is deliberately not used as a
 * fallback — it is the megamorphic loop the partitioned reader already beats, plus a cursor indirection.
 */
public final class CallerGlobReader implements GlobReader {
    private final ToGlobCallerAll<MemorySegment, Long, ReadContext> caller;

    private CallerGlobReader(ToGlobCallerAll<MemorySegment, Long, ReadContext> caller) {
        this.caller = caller;
    }

    /**
     * @return the generated walk of this type, or null when nothing is installed to generate. One instance
     * per type, like every other {@link GlobReader} : nothing here is per record.
     */
    public static GlobReader readerFor(GlobType type, HandleAccess[] handleAccesses) {
        final ToGlobCallerFactory factory = ToGlobCallerFactory.generated();
        if (factory == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        final ToGlobFunction<MemorySegment, Long, ReadContext>[] functions =
                new ToGlobFunction[handleAccesses.length];
        for (int i = 0; i < handleAccesses.length; i++) {
            functions[i] = wrap(handleAccesses[i]);
        }
        // the purpose is constant in the source, the type is what tells two callers apart -- see CallerName
        return new CallerGlobReader(factory.create("offheap.readAll." + type.getName(), functions));
    }

    /**
     * The offset is boxed here, <b>once per record</b> and not once per field : the same {@code Long} is
     * handed to every function of the pass. It is the price of carrying a {@code long} through a SPI whose
     * contexts are objects, and it is the cheap way to pay it — the alternative, a mutable cursor object
     * holding segment and offset, costs two field loads per field instead of one unboxing and, far worse,
     * makes the reader stateful, which a {@link GlobReader} must never be.
     */
    @Override
    public void read(MutableGlob data, MemorySegment memorySegment, long offset, ReadContext readContext) {
        caller.call(data, memorySegment, offset, readContext);
    }

    /**
     * A generated call site makes its {@code ToGlobFunction} constant; whether the {@code HandleAccess}
     * behind it becomes constant too is what decides if the read itself inlines, and that depends on the
     * class holding it : C2 trusts the final instance fields of records, hidden classes and lambdas, not
     * those of an ordinary class. Hence a record for the leaves — and deliberately not one for the kinds that
     * recurse into another record, {@link NestedRead} below.
     */
    private record FieldRead(HandleAccess access)
            implements ToGlobFunction<MemorySegment, Long, ReadContext> {

        public void call(MutableGlob data, MemorySegment memorySegment, Long offset, ReadContext readContext) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
    }

    /**
     * The four Glob-valued kinds, behind an ordinary class : its {@code access} is <em>not</em> folded, so the
     * call keeps an inlining barrier where a sub-record hangs. Measured elsewhere in the workspace (see
     * globs-generate's PERFORMANCE.md) : folding the descent instead inlines the child into the parent and
     * then the grandchild, up to the point where the whole method stops being compiled well, and costs more
     * than the dispatch it saves.
     */
    private static final class NestedRead implements ToGlobFunction<MemorySegment, Long, ReadContext> {
        private final HandleAccess access;

        private NestedRead(HandleAccess access) {
            this.access = access;
        }

        public void call(MutableGlob data, MemorySegment memorySegment, Long offset, ReadContext readContext) {
            access.readAtOffset(data, memorySegment, offset, readContext);
        }
    }

    private static ToGlobFunction<MemorySegment, Long, ReadContext> wrap(HandleAccess handleAccess) {
        return switch (handleAccess) {
            case GlobHandleAccess a -> new NestedRead(a);
            case GlobInlineHandleAccess a -> new NestedRead(a);
            case GlobArrayHandleAccess a -> new NestedRead(a);
            case GlobArrayInlineHandleAccess a -> new NestedRead(a);
            default -> new FieldRead(handleAccess);
        };
    }
}
