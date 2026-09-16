package org.globsframework.shared.mem.tree;

import org.globsframework.core.model.caller.LoopToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobCallerService;

/**
 * Installs core's looped factory where a generating one is expected, so that {@code ReadAllPerf}'s `loop`
 * arm measures what a JVM with nothing to generate would get if it asked for the typed shape anyway : a
 * reflective Proxy, which boxes every primitive on every call.
 * <p>
 * It is the floor of the comparison. Production never installs this — {@code generated()} answering null is
 * what keeps the partitioned reader — and globs-off-heap deliberately does not fall back on it.
 */
public class LoopToGlobCallerServiceForTest implements ToGlobCallerService {

    public ToGlobCallerFactory factory() {
        return LoopToGlobCallerFactory.INSTANCE;
    }
}
