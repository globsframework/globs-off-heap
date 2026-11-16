package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.hash.OffHeapHashAccess;
import org.globsframework.shared.mem.hash.OffHeapReadHashService;
import org.globsframework.shared.mem.hash.OffHeapUpdaterService;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hash indices on 3 and 4 field keys, the Three/FourFieldsMutableKey of core, through the readAndCheck path:
 * found, missed, and told apart from a key of the same hash ("Aa" and "BB" share their String hashCode, so two
 * keys differing only there collide on the whole 32 bits and only FunctionalKey.matches separates them).
 */
class OffHeapHashMultiFieldKeyTest {

    public static class Quote {
        public static final GlobType TYPE;
        public static final LongField id;
        public static final StringField code;
        public static final IntegerField version;
        public static final LongField venue;
        public static final StringField payload;

        static {
            final GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("Quote");
            id = typeBuilder.declareLongField("id");
            code = typeBuilder.declareStringField("code");
            version = typeBuilder.declareIntegerField("version");
            venue = typeBuilder.declareLongField("venue");
            payload = typeBuilder.declareStringField("payload");
            TYPE = typeBuilder.build();
        }

        static MutableGlob create(long id, String code, int version, long venue, String payload) {
            return TYPE.instantiate().set(Quote.id, id).set(Quote.code, code).set(Quote.version, version)
                    .set(Quote.venue, venue).set(Quote.payload, payload);
        }
    }

    private static final FunctionalKeyBuilder K3 = FunctionalKeyBuilderFactory.create(Quote.TYPE)
            .add(Quote.id).add(Quote.code).add(Quote.version).create();
    private static final FunctionalKeyBuilder K4 = FunctionalKeyBuilderFactory.create(Quote.TYPE)
            .add(Quote.id).add(Quote.code).add(Quote.version).add(Quote.venue).create();

    private static List<Glob> data(int size) {
        List<Glob> globs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            // "Aa" and "BB": same String.hashCode, so same key hash on every other field equal
            globs.add(Quote.create(i, "Aa", i % 7, i % 3, "aa " + i));
            globs.add(Quote.create(i, "BB", i % 7, i % 3, "bb " + i));
        }
        return globs;
    }

    private static OffHeapHashServiceImpl service() {
        OffHeapHashServiceImpl service = new OffHeapHashServiceImpl(Quote.TYPE);
        service.declare("k3", K3, 10_000);
        service.declare("k4", K4, 10_000);
        return service;
    }

    @Test
    void sameHashKeysAreToldApart() {
        assertEquals("Aa".hashCode(), "BB".hashCode());
        Glob aa = Quote.create(1, "Aa", 1, 1, "");
        Glob bb = Quote.create(1, "BB", 1, 1, "");
        assertEquals(K3.proxy(aa).hashCode(), K3.proxy(bb).hashCode());
        assertEquals(K4.proxy(aa).hashCode(), K4.proxy(bb).hashCode());
        assertNotEquals(K3.proxy(aa), K3.proxy(bb));
    }

    @Test
    void readByThreeAndFourFieldKeys() throws IOException {
        final int size = 2_000;
        List<Glob> globs = data(size);
        Path storagePath = Files.createTempDirectory("offheap-hash-multikey");
        OffHeapHashServiceImpl service = service();
        service.createWriter(storagePath).save(globs);

        OffHeapReadHashService readService = service.createReader(storagePath, Arena.ofShared(), GlobType::instantiate);
        OffHeapHashAccess k3 = readService.getReader("k3");
        OffHeapHashAccess k4 = readService.getReader("k4");
        for (Glob glob : globs) {
            assertEquals(glob.get(Quote.payload), k3.get(K3.proxy(glob)).get(Quote.payload));
            assertEquals(glob.get(Quote.payload), k4.get(K4.proxy(glob)).get(Quote.payload));
        }

        // misses: one field off, including a code that hashes like an existing one
        assertNull(k3.get(K3.create().set(Quote.id, 1L).set(Quote.code, "Ab").set(Quote.version, 1).create()));
        assertNull(k3.get(K3.create().set(Quote.id, 1L).set(Quote.code, "Aa").set(Quote.version, 2).create()));
        assertNull(k4.get(K4.create().set(Quote.id, 1L).set(Quote.code, "BB").set(Quote.version, 1).set(Quote.venue, 2L).create()));
        assertNull(k4.get(K4.create().set(Quote.id, (long) size).set(Quote.code, "Aa").set(Quote.version, 0).set(Quote.venue, 0L).create()));
    }

    @Test
    void updateReplacesTheMatchingEntryOnly() throws IOException {
        final int size = 200;
        List<Glob> globs = data(size);
        Path storagePath = Files.createTempDirectory("offheap-hash-multikey-update");
        OffHeapHashServiceImpl service = service();
        service.createWriter(storagePath).save(globs);

        OffHeapUpdaterService updater = service.createUpdater(storagePath, Arena.ofShared());
        for (int i = 0; i < size; i += 2) {
            updater.update(Quote.create(i, "BB", i % 7, i % 3, "updated " + i));
        }

        OffHeapReadHashService readService = service.createReader(storagePath, Arena.ofShared(), GlobType::instantiate);
        OffHeapHashAccess k3 = readService.getReader("k3");
        OffHeapHashAccess k4 = readService.getReader("k4");
        for (int i = 0; i < size; i++) {
            Glob aa = Quote.create(i, "Aa", i % 7, i % 3, null);
            Glob bb = Quote.create(i, "BB", i % 7, i % 3, null);
            String expectedBB = i % 2 == 0 ? "updated " + i : "bb " + i;
            assertEquals("aa " + i, k3.get(K3.proxy(aa)).get(Quote.payload));
            assertEquals("aa " + i, k4.get(K4.proxy(aa)).get(Quote.payload));
            assertEquals(expectedBB, k3.get(K3.proxy(bb)).get(Quote.payload));
            assertEquals(expectedBB, k4.get(K4.proxy(bb)).get(Quote.payload));
        }
    }
}
