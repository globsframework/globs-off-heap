package org.globsframework.shared.mem.hash;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.model.Glob;

import java.util.Map;

public interface OffHeapWriter {

    void save(Entity entity, Map<FunctionalKey, Glob> data);

}
