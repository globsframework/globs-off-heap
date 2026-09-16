package org.globsframework.shared.mem.tree;

import org.globsframework.core.model.caller.LoopToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobCallerService;

/**
 * Installs core's looped factory where a generating one is expected, so that {@code ReadAllPerf}'s `loop`
 * arm goes through the whole CallerGlobReader path — the same wrappers, the same boxed offset, the same
 * per-field unboxing — with the unrolling as the only difference against the `on` arm.
 * <p>
 * It is what separates what the shape of the emitted code costs from what carrying the arguments costs;
 * production never installs this, `generated()` answering null is what keeps the partitioned reader.
 */
public class LoopToGlobCallerServiceForTest implements ToGlobCallerService {

    public ToGlobCallerFactory factory() {
        return LoopToGlobCallerFactory.INSTANCE;
    }
}
