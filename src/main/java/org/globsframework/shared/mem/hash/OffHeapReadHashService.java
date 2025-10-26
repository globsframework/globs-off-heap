package org.globsframework.shared.mem.hash;

public interface OffHeapReadHashService {

    OffHeapHashAccess getReader(String name);

}
