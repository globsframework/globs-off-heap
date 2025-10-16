package org.globsframework.shared.mem.tree;

import org.globsframework.core.model.Glob;

public interface DataConsumer {
    void accept(Glob glob);
}
