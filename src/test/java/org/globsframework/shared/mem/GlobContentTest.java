package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.impl.Dummy1Type;
import org.globsframework.shared.mem.impl.Dummy2Type;
import org.globsframework.shared.mem.impl.Dummy3Type;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GlobContentTest {

    @Test
    void withGlobInGlob() throws IOException {
        final MutableGlob d1 = Dummy1Type.TYPE.instantiate();
        final MutableGlob d2 = Dummy1Type.TYPE.instantiate();
        final MutableGlob d21 = Dummy2Type.create("d21");
        final MutableGlob d22 = Dummy2Type.create("d22");
        d1.set(Dummy1Type.subObject, d21);
        d1.set(Dummy1Type.subObjectInline, d22);
//        d22.set(Dummy2Type.subObjects, new MutableGlob[]{Dummy3Type.create("ext1"),Dummy3Type.create("ext2")});
//        d22.set(Dummy2Type.subObjectsInline, new MutableGlob[]{Dummy3Type.create("inl1"),Dummy3Type.create("inl2")});
        d2.set(Dummy1Type.subObjectInline, Dummy2Type.create("d23"));

        final FunctionalKeyBuilder uniqueFunctionalKeyBuilder =
                FunctionalKeyBuilderFactory.create(Dummy1Type.TYPE)
                        .add(Dummy1Type.id)
                        .create();

        OffHeapService offHeapService = OffHeapService.create(Dummy1Type.TYPE);

        Arena arena = Arena.ofShared();

        final Path testMultiGlob = Files.createTempDirectory("testMultiGlob");
        Files.createDirectories(testMultiGlob);
        OffHeapWriteService offHeapWriteService = offHeapService.createWrite(testMultiGlob);

        offHeapWriteService.save(List.of(d1, d2));

        offHeapWriteService.close();
        OffHeapReadService readHeapService = offHeapService.createRead(testMultiGlob, arena);
        List<Glob> readData = new ArrayList<>();
        readHeapService.readAll(readData::add);
        Assertions.assertEquals(2, readData.size());
        Assertions.assertEquals("""
[{ "_kind":"Dummy1Type", "id":0, "subObjectInline":{ "_kind":"Dummy2Type", "name":"d22", "aligned":0}, "subObject":{ "_kind":"Dummy2Type", "name":"d21", "aligned":0}}, { "_kind":"Dummy1Type", "id":0, "subObjectInline":{ "_kind":"Dummy2Type", "name":"d23", "aligned":0}}]                """, readData.toString());

    }
}
