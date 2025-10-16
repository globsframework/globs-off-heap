package org.globsframework.shared.mem.tree;

import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.json.GSonUtils;
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
        final MutableGlob d1 = Dummy1Type.TYPE.instantiate().set(Dummy1Type.id, 1);
        final MutableGlob d2 = Dummy1Type.TYPE.instantiate().set(Dummy1Type.id, 2);
        final MutableGlob d21 = Dummy2Type.create("d21");
        final MutableGlob d22 = Dummy2Type.create("d22");
        d1.set(Dummy1Type.subObject, d21);
        d1.set(Dummy1Type.subObjectInline, d22);
        d22.set(Dummy2Type.subObjects, new MutableGlob[]{Dummy3Type.create("ext1"),Dummy3Type.create("ext2")});
        d22.set(Dummy2Type.subObjectsInline, new MutableGlob[]{Dummy3Type.create("inl1"),Dummy3Type.create("inl2")});
        d2.set(Dummy1Type.subObjectInline, Dummy2Type.create("d23"));

        OffHeapTreeService offHeapService = OffHeapTreeService.create(Dummy1Type.TYPE);

        Arena arena = Arena.ofShared();

        final Path testMultiGlob = Files.createTempDirectory("testMultiGlob");
        Files.createDirectories(testMultiGlob);
        OffHeapWriteTreeService offHeapWriteService = offHeapService.createWrite(testMultiGlob);

        offHeapWriteService.save(List.of(d1, d2));

        offHeapWriteService.close();
        OffHeapReadTreeService readHeapService = offHeapService.createRead(testMultiGlob, arena);
        List<Glob> readData = new ArrayList<>();
        readHeapService.readAll(readData::add);
        Assertions.assertEquals(2, readData.size());
        Glob a1 = readData.get(0);
        Glob a2 = readData.get(1);
        if (a1.get(Dummy1Type.id) != 1) {
            Glob tmp = a1;
            a1= a2;
            a2 = tmp;
        }
        Assertions.assertEquals(GSonUtils.encode(d1, false),
                GSonUtils.encode(a1, false));
        Assertions.assertEquals(GSonUtils.encode(d2, false),
                GSonUtils.encode(a2, false));
    }

    public static String EXPECTED = """
[
  {
    "id": 0,
    "subObjectInline": {
      "name": "d23",
      "aligned": 0
    }
  },
  {
    "id": 0,
    "subObjectInline": {
      "name": "d22",
      "subObjectsInline": [
        {
          "data": "inl1",
          "unaligned": false
        },
        {
          "data": "inl2",
          "unaligned": false
        }
      ],
      "subObjects": [
        {
          "data": "ext1",
          "unaligned": false
        },
        {
          "data": "ext2",
          "unaligned": false
        }
      ],
      "aligned": 0
    },
    "subObject": {
      "name": "d21",
      "aligned": 0
    }
  }
]""";


}
