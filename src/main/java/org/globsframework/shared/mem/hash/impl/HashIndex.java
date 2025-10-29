package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKeyBuilder;

public record HashIndex(String name, FunctionalKeyBuilder keyBuilder, int size) {
}
