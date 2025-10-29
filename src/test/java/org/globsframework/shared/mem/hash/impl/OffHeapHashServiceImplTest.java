package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.hash.OffHeapHashAccess;
import org.globsframework.shared.mem.hash.OffHeapReadHashService;
import org.globsframework.shared.mem.hash.OffHeapUpdaterService;
import org.globsframework.shared.mem.hash.OffHeapWriteHashService;
import org.globsframework.shared.mem.impl.Dummy1Type;
import org.globsframework.shared.mem.impl.Dummy2Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        Path storagePath = Files.createTempDirectory("offheap-hash");
        Files.createDirectories(storagePath);

        final OffHeapWriteHashService offHeapWriteHashService = offHeapHashService.createWriter(storagePath);
        offHeapWriteHashService.save(globs);
        final OffHeapReadHashService offHeapReadHashService =
                offHeapHashService.createReader(storagePath, Arena.ofShared(), GlobType::instantiate);
        final OffHeapHashAccess reader = offHeapReadHashService.getReader("id");
        for (Glob glob : globs) {
            final FunctionalKey proxy = keyBuilder.proxy(glob);
            Glob glob1 = reader.get(proxy);
            assertNotNull(glob1);
            assertEquals("sub " + glob.get(Dummy1Type.id), glob1.get(Dummy1Type.subObject).get(Dummy2Type.name));
//            final Glob glob2 = data.get(proxy);
//            assertNotNull(glob2);
//            assertEquals("sub " + glob.get(Dummy1Type.id), glob2.get(Dummy1Type.subObject).get(Dummy2Type.name));
        }

        {
            final long start = System.nanoTime();
            for (Glob glob : globs) {
                final Glob glob1 = reader.get(keyBuilder.proxy(glob));
                assertNotNull(glob1);
            }
            final long nano = System.nanoTime() - start;
            System.out.println("Read " + TimeUnit.NANOSECONDS.toMicros(nano) + " us => " + nano / globs.size() + " ns/search");
        }
        {
            final long start = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                Assertions.assertNull(reader.get(keyBuilder.create()
                        .set(Dummy1Type.id, -1 - i).create()));
            }
            final long nano = System.nanoTime() - start;
            System.out.println("Read " + TimeUnit.NANOSECONDS.toMicros(nano) + " us => " + nano / globs.size() + " ns/search");
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

    @Test
    void withSameHash() throws IOException {
        FunctionalKeyBuilder keyBuilder = FunctionalKeyBuilderFactory.create(MultiKey.TYPE)
                .add(MultiKey.id)
                .create();

//        final MutableFunctionalKey set1 = keyBuilder.create().set(MultiKey.id, 1);
//        final int hashCode = set1.create().hashCode();
//        long i = 2;
//        while (i < Long.MAX_VALUE) {
//            set1.set(MultiKey.val1, i);
//            if (set1.getShared().hashCode() == hashCode) {
//                System.out.println("OffHeapHashServiceImplTest.withSameHash " + i);
//            }
//            i++;
//        }
        final MutableGlob g1 = MultiKey.TYPE.instantiate().set(MultiKey.id, 1).set(MultiKey.val1, 1);
        final MutableGlob g2 = MultiKey.TYPE.instantiate().set(MultiKey.id, 4294967296L).set(MultiKey.val1, 2);
        final MutableGlob g3 = MultiKey.TYPE.instantiate().set(MultiKey.id, 8589934595L).set(MultiKey.val1, 3);
        Path storagePath = Files.createTempDirectory("offheap-hash");
        Files.createDirectories(storagePath);

        OffHeapHashServiceImpl offHeapHashService = new OffHeapHashServiceImpl(MultiKey.TYPE);
        offHeapHashService.declare("id", keyBuilder, 2000);
        final OffHeapWriteHashService writer = offHeapHashService.createWriter(storagePath);
        writer.save(List.of(g1, g2, g3));

        final OffHeapReadHashService reader = offHeapHashService.createReader(storagePath, Arena.ofShared(), GlobType::instantiate);
        final OffHeapHashAccess index = reader.getReader("id");
        final Glob gg1 = index.get(keyBuilder.proxy(g1));
        final Glob gg2 = index.get(keyBuilder.proxy(g2));
        final Glob gg3 = index.get(keyBuilder.proxy(g3));
        Assertions.assertNotNull(gg1);
        Assertions.assertNotNull(gg2);
        Assertions.assertNotNull(gg3);
        Assertions.assertEquals(g1.get(MultiKey.val1), gg1.get(MultiKey.val1));
        Assertions.assertEquals(g2.get(MultiKey.val1), gg2.get(MultiKey.val1));
        Assertions.assertEquals(g3.get(MultiKey.val1), gg3.get(MultiKey.val1));
    }

    public static class MultiKey {
        public static final GlobType TYPE;

        public static final LongField id;

        public static final LongField val1;


        static {
            final GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("MultiKey");
            TYPE = typeBuilder.unCompleteType();
            id = typeBuilder.declareLongField("id");
            val1 = typeBuilder.declareLongField("val1");
            typeBuilder.complete();
        }
    }

    @Test
    void update() throws IOException {
        final int inserted = 10_000;

        OffHeapHashServiceImpl offHeapHashService = new OffHeapHashServiceImpl(Dummy1Type.TYPE);
        final FunctionalKeyBuilder keyBuilder = FunctionalKeyBuilderFactory.create(Dummy1Type.TYPE)
                .add(Dummy1Type.id)
                .create();
        offHeapHashService.declare("id", keyBuilder, inserted * 2 + 100);

        List<Glob> globs = new ArrayList<>();
        for (int i = 0; i < inserted; i++) {
            final Glob set = Dummy1Type.create(i, "name " + i, null, Dummy2Type.create("sub " + i));
            globs.add(set);
        }
        Path storagePath = Files.createTempDirectory("offheap-hash");
        Files.createDirectories(storagePath);

        final OffHeapWriteHashService offHeapWriteHashService = offHeapHashService.createWriter(storagePath);
        offHeapWriteHashService.save(globs);
        final OffHeapReadHashService offHeapReadHashService =
                offHeapHashService.createReader(storagePath, Arena.ofShared(), GlobType::instantiate);
        final OffHeapHashAccess reader = offHeapReadHashService.getReader("id");

        final OffHeapUpdaterService updater = offHeapHashService.createUpdater(storagePath, Arena.ofShared());

        int end = inserted + 100;
        int start = Math.max(0, end - 100);
        for (int i = start; i < end; i++) {
//        int i = 100;
            final Glob data = Dummy1Type.create(i, "name " + (i + 1), null, Dummy2Type.create("sub " + (i + 2)));
            updater.update(data);
        }

        for (int i = start; i < end; i++) {
            final Glob glob = reader.get(keyBuilder.create().set(Dummy1Type.id, i).create());
            Assertions.assertNotNull(glob, "Glob is null at " + i);
            Assertions.assertEquals("name " + (i + 1), glob.get(Dummy1Type.name));
            Assertions.assertEquals("sub " + (i + 2), glob.get(Dummy1Type.subObject).get(Dummy2Type.name));
        }
    }
}