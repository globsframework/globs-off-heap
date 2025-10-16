package org.globsframework.shared.mem.hash;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;

import java.util.Optional;
import java.util.function.Predicate;

public interface OffHeapHashAccess {
    Optional<Glob> get(FunctionalKey key);

    Optional<Glob> get(FunctionalKey key, Predicate<Field> fields);

    boolean exist(FunctionalKey key);
}
