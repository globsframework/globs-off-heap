package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.hash.OffHeapHashAccess;
import org.globsframework.shared.mem.hash.OffHeapReadHashService;
import org.globsframework.shared.mem.hash.OffHeapWriteHashService;
import org.globsframework.shared.mem.impl.Dummy1Type;
import org.globsframework.shared.mem.impl.Dummy2Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OffHeapHashServiceImplTest {

    @Test
    void name() throws IOException {
        OffHeapHashServiceImpl offHeapHashService = new OffHeapHashServiceImpl(Dummy1Type.TYPE);
        final FunctionalKeyBuilder keyBuilder = FunctionalKeyBuilderFactory.create(Dummy1Type.TYPE)
                .add(Dummy1Type.id)
                .create();
        offHeapHashService.declare("id", keyBuilder, 20_000);

//        Map<FunctionalKey, Glob> data = new HashMap<>();
        List<Glob> globs = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            final MutableGlob set = Dummy1Type.TYPE.instantiate()
                    .set(Dummy1Type.id, i)
                    .set(Dummy1Type.subObject, Dummy2Type.create("sub " + i));
            globs.add(set);
//            data.put(keyBuilder.create(set), set);
        }
        Path storagePath = Path.of("/tmp/offheap-hash");
        Files.createDirectories(storagePath);

        final OffHeapWriteHashService offHeapWriteHashService = offHeapHashService.create(storagePath);
        offHeapWriteHashService.save(globs);
        final OffHeapReadHashService offHeapReadHashService =
                offHeapHashService.create(storagePath, Arena.ofShared(), GlobType::instantiate);
        final OffHeapHashAccess reader = offHeapReadHashService.getReader("id");
        for (Glob glob : globs) {
            final FunctionalKey proxy = keyBuilder.proxy(glob);
            final Optional<Glob> glob1 = reader.get(proxy);
            assertTrue(glob1.isPresent());
            assertEquals("sub " + glob.get(Dummy1Type.id), glob1.get().get(Dummy1Type.subObject).get(Dummy2Type.name));
//            final Glob glob2 = data.get(proxy);
//            assertNotNull(glob2);
//            assertEquals("sub " + glob.get(Dummy1Type.id), glob2.get(Dummy1Type.subObject).get(Dummy2Type.name));
        }

        {
            final long start = System.nanoTime();
            for (Glob glob : globs) {
                final Optional<Glob> glob1 = reader.get(keyBuilder.proxy(glob), field -> field == Dummy1Type.id);
                assertTrue(glob1.isPresent());
            }
            final long nanp = System.nanoTime() - start;
            System.out.println("Read " + TimeUnit.NANOSECONDS.toMicros(nanp) + " us => " + nanp / globs.size() + " ns/search");
        }
        {
            final long start = System.nanoTime();
            for (Glob glob : globs) {
                Assertions.assertTrue(reader.exist(keyBuilder.proxy(glob)));
            }
            final long nanp = System.nanoTime() - start;
            System.out.println("Read " + TimeUnit.NANOSECONDS.toMicros(nanp) + " us => " + nanp / globs.size() + " ns/search");
        }
//        {
//            final long start = System.nanoTime();
//            for (Glob glob : globs) {
//                final Glob glob1 = data.get(keyBuilder.proxy(glob));
//                assertNotNull(glob1);
//            }
//            final long nanp = System.nanoTime() - start;
//            System.out.println("Read " + TimeUnit.NANOSECONDS.toMicros(nanp) + " us => " + nanp / globs.size() + " ns/search");
//        }
    }
}