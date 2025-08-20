package org.globsframework.shared.mem.impl.write;

import org.globsframework.shared.mem.impl.StringAddrAccessor;

public record SaveContext(StringAddrAccessor stringAddrAccessor, FreeOffset freeOffset, Flush flush) {
}
