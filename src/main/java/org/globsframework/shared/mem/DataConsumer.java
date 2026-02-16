package org.globsframework.shared.mem;

import org.globsframework.core.model.Glob;

public interface DataConsumer {
    boolean accept(Glob glob);
}
