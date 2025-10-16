package org.globsframework.shared.mem.hash;

public interface OffHeapReadHashService {

    OffHeapUpdater getUpdater();

    OffHeapHashAccess getReader(String name);

}
