package org.globsframework.shared.mem.tree.impl.read;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.caller.ToGlobCallerFactory;
import org.globsframework.shared.mem.field.handleacces.HandleAccess;

import java.lang.foreign.MemorySegment;

/**
 * The read walk as a class emitted by globs-generate : the {@code HandleAccess[]} unrolled into one call site
 * per field, each reading its accessor from a {@code static final} of the generated class, so the receiver is
 * a JIT constant there — where {@link PartitionedGlobReader} has one call site per field <em>kind</em>, shared
 * by every type of the process.
 * <p>
 * <b>Nothing is adapted.</b> The emitted class implements {@link GlobReader} itself and calls
 * {@link HandleAccess#readAtOffset} itself, because the two happen to take the same arguments —
 * {@code (MutableGlob, MemorySegment, long, ReadContext)} — which is exactly what the typed
 * {@link ToGlobCallerFactory#create(String, Object[], Class, Class, Class...)} shape is for. So there is no
 * wrapper object, no generic bridge, no checkcast and no boxed offset: the walk reads its record through the
 * accessors it was given, one LLOAD for the offset, and the generated class is the reader rather than
 * something holding one.
 * <p>
 * That is what the first attempt at this could not do. Going through {@code ToGlobFunction}, whose three
 * contexts are objects, cost a call level, three casts per field and a boxed {@code Long} per record —
 * measured at four times what the unrolling earns back. See "The generated walk" in CLAUDE.md.
 * <p>
 * Built only when something is installed to generate ({@code -Dglobs.caller.toGlob=...}) :
 * {@link ToGlobCallerFactory#generated()} answers null otherwise and the walk stays on
 * {@link PartitionedGlobReader}. Core's looped factory is deliberately not used as a fallback — its typed
 * shape is a reflective proxy, which is slower than anything here.
 */
public final class GeneratedWalkReader {

    private GeneratedWalkReader() {
    }

    /**
     * @return the generated walk of this type, or null when nothing is installed to generate. Stateless and
     * shared, like every other {@link GlobReader} : the arguments carry the record, so there is nothing to
     * hold on to.
     */
    public static GlobReader readerFor(GlobType type, HandleAccess[] handleAccesses) {
        final ToGlobCallerFactory factory = ToGlobCallerFactory.generated();
        if (factory == null) {
            return PartitionedGlobReader.create(handleAccesses);
        }
        // the purpose is constant in the source, the type is what tells two callers apart -- see CallerName
        return factory.create("offheap.readAll." + type.getName(), handleAccesses,
                GlobReader.class, HandleAccess.class,
                MutableGlob.class, MemorySegment.class, long.class, ReadContext.class);
    }
}
