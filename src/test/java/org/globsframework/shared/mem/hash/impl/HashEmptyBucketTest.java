package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.hash.OffHeapHashAccess;
import org.globsframework.shared.mem.hash.OffHeapReadHashService;
import org.globsframework.shared.mem.hash.OffHeapWriteHashService;
import org.globsframework.shared.mem.impl.Dummy1Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class HashEmptyBucketTest {

    @Test
    void testEmptyBucket() throws IOException {
        OffHeapHashServiceImpl offHeapHashService = new OffHeapHashServiceImpl(Dummy1Type.TYPE);
        final FunctionalKeyBuilder keyBuilder = FunctionalKeyBuilderFactory.create(Dummy1Type.TYPE)
                .add(Dummy1Type.id)
                .create();
        
        // Use a size that is power of 2, e.g., 4. 
        // We will insert 1 element. There will definitely be empty buckets.
        offHeapHashService.declare("id", keyBuilder, 4);

        Path storagePath = Files.createTempDirectory("offheap-hash-empty");
        
        final Glob glob = Dummy1Type.create(1, "name 1", null, null);
        
        final OffHeapWriteHashService writer = offHeapHashService.createWriter(storagePath);
        writer.save(List.of(glob));
        
        final OffHeapReadHashService reader = offHeapHashService.createReader(storagePath, Arena.ofShared(), GlobType::instantiate);
        final OffHeapHashAccess index = reader.getReader("id");
        
        // This should find the glob
        final Glob found = index.get(keyBuilder.create().set(Dummy1Type.id, 1).create());
        Assertions.assertNotNull(found);
        Assertions.assertEquals(1, found.get(Dummy1Type.id));
        
        // This should NOT find a glob and should NOT crash
        // id = 2 might hash to a different bucket
        final Glob notFound = index.get(keyBuilder.create().set(Dummy1Type.id, 2).create());
        Assertions.assertNull(notFound);
    }
}
