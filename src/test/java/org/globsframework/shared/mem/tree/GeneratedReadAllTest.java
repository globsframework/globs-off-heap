package org.globsframework.shared.mem.tree;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.caller.ToGlobCallerFactory;
import org.globsframework.core.model.caller.ToGlobCallerService;
import org.globsframework.json.GSonUtils;
import org.globsframework.model.generator.AsmCallerWriteGenerator;
import org.globsframework.model.generator.AsmCallerWriteGeneratorService;
import org.globsframework.shared.mem.impl.Dummy1Type;
import org.globsframework.shared.mem.impl.Dummy2Type;
import org.globsframework.shared.mem.impl.Dummy3Type;
import org.globsframework.shared.mem.tree.impl.OffHeapGlobTypeGroupLayoutImpl;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.read.CallerGlobReader;
import org.globsframework.shared.mem.tree.impl.read.GlobReader;
import org.globsframework.shared.mem.tree.impl.read.PartitionedGlobReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The generated walk of {@code readAll} has to read exactly what the partitioned reader reads, so every test
 * here reads one store twice — once with nothing installed to generate, once with globs-generate installed —
 * and compares the two passes field by field, through the JSON of each record.
 * <p>
 * The property is global and cached, hence the reset in {@link #restoreDefaultCallerFactory()} : a test that
 * left it set would silently move every test after it onto the generated path.
 */
public class GeneratedReadAllTest {
    private static final String PROPERTY = "globs.caller.toGlob";

    @AfterEach
    void restoreDefaultCallerFactory() {
        System.clearProperty(PROPERTY);
        System.clearProperty(AsmCallerWriteGenerator.CHUNK_PROPERTY);
        ToGlobCallerService.Builder.reset();
    }

    /** Every leaf kind the layout supports, i.e. every kind read through the folded record. */
    @Test
    public void everyFieldKindReadsTheSame() throws IOException {
        final PerfTypeFamily family = PerfTypeFamily.create(PerfTypeFamily.Shape.narrow, "GeneratedReadAllFlat");
        final List<Glob> written = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            written.add(family.createData(i, LocalDate.of(1900 + i, i % 11 + 1, i % 28 + 1)));
        }
        assertSameReadAll(family.type, written);
    }

    /** The four Glob-valued kinds, which the generated caller deliberately keeps unfolded. */
    @Test
    public void nestedAndInlineGlobsReadTheSame() throws IOException {
        final MutableGlob d1 = Dummy1Type.TYPE.instantiate().set(Dummy1Type.id, 1);
        final MutableGlob d2 = Dummy1Type.TYPE.instantiate().set(Dummy1Type.id, 2).set(Dummy1Type.name, "d2");
        final MutableGlob sub = Dummy2Type.create("d22");
        sub.set(Dummy2Type.subObjects, new MutableGlob[]{Dummy3Type.create("ext1"), Dummy3Type.create("ext2")});
        sub.set(Dummy2Type.subObjectsInline, new MutableGlob[]{Dummy3Type.create("inl1"), Dummy3Type.create("inl2")});
        d1.set(Dummy1Type.subObject, Dummy2Type.create("d21"));
        d1.set(Dummy1Type.subObjectInline, sub);
        d2.set(Dummy1Type.subObjectInline, Dummy2Type.create("d23"));

        assertSameReadAll(Dummy1Type.TYPE, List.of(d1, d2));
    }

    /** What the two passes above are worth depends on the second one really being generated. */
    @Test
    public void theWalkIsGeneratedOnlyWhenSomethingIsInstalledToGenerate() {
        Assertions.assertNull(ToGlobCallerFactory.generated());
        Assertions.assertFalse(walkReaderOf(Dummy1Type.TYPE) instanceof CallerGlobReader);

        installGenerator();
        Assertions.assertNotNull(ToGlobCallerFactory.generated());
        Assertions.assertInstanceOf(CallerGlobReader.class, walkReaderOf(Dummy1Type.TYPE));
    }

    private static GlobReader walkReaderOf(GlobType type) {
        return typeInfoOf(type).walkReader;
    }

    private static OffHeapTypeInfo typeInfoOf(GlobType type) {
        return OffHeapTypeInfo.create(type, OffHeapGlobTypeGroupLayoutImpl.create(type).getPrimaryGroupLayout());
    }

    /**
     * Read once per arm : the single emitted method, and a chunk small enough to make several parts of every
     * type here — the last part is then the only one the field count does not fill, which is the case a
     * chunked emitter gets wrong.
     */
    /**
     * A GlobReader holds nothing per record, the generated one included : the offset is boxed into the call
     * rather than parked in the reader, so one instance answers every walk of the type and every thread.
     * Reading the same records twice through the very same reader is what a stateful one would not survive.
     */
    @Test
    public void theGeneratedWalkIsOneSharedStatelessReader() throws IOException {
        installGenerator();
        final OffHeapTypeInfo typeInfo = typeInfoOf(Dummy1Type.TYPE);

        Assertions.assertInstanceOf(CallerGlobReader.class, typeInfo.walkReader);
        Assertions.assertSame(typeInfo.walkReader, typeInfo.walkReader);
        Assertions.assertInstanceOf(PartitionedGlobReader.class, typeInfo.reader);

        final Path directory = Files.createTempDirectory("generatedReadAllShared");
        final List<Glob> written = List.of(Dummy1Type.TYPE.instantiate().set(Dummy1Type.id, 1),
                Dummy1Type.TYPE.instantiate().set(Dummy1Type.id, 2));
        OffHeapTreeService.create(Dummy1Type.TYPE).createWrite(directory).save(written);

        final OffHeapTreeService service = OffHeapTreeService.create(Dummy1Type.TYPE);
        try (Arena arena = Arena.ofShared()) {
            final OffHeapReadTreeService readService = service.createRead(directory, arena);
            final List<String> first = new ArrayList<>();
            final List<String> second = new ArrayList<>();
            readService.readAll(glob -> first.add(GSonUtils.encode(glob, false)));
            readService.readAll(glob -> second.add(GSonUtils.encode(glob, false)));
            Assertions.assertEquals(first, second);
            Assertions.assertEquals(written.size(), first.size());
        }
    }

    private void assertSameReadAll(GlobType type, List<Glob> written) throws IOException {
        final Path directory = Files.createTempDirectory("generatedReadAll");
        OffHeapTreeService.create(type).createWrite(directory).save(written);

        final List<String> partitioned = readAll(type, directory);
        Assertions.assertEquals(written.size(), partitioned.size());

        installGenerator(0);
        Assertions.assertEquals(partitioned, readAll(type, directory));

        installGenerator(4);
        Assertions.assertEquals(partitioned, readAll(type, directory), "chunked walk");
    }

    private void installGenerator() {
        installGenerator(0);
    }

    private void installGenerator(int chunk) {
        System.setProperty(PROPERTY, AsmCallerWriteGeneratorService.class.getName());
        System.setProperty(AsmCallerWriteGenerator.CHUNK_PROPERTY, Integer.toString(chunk));
        ToGlobCallerService.Builder.reset();
    }

    /** A service of its own per pass : the walk reader is built with the type info, at create time. */
    private List<String> readAll(GlobType type, Path directory) throws IOException {
        final List<String> read = new ArrayList<>();
        final OffHeapTreeService service = OffHeapTreeService.create(type);
        try (Arena arena = Arena.ofShared()) {
            service.createRead(directory, arena).readAll(glob -> {
                read.add(GSonUtils.encode(glob, false));
                return true;
            });
        }
        return read;
    }
}
