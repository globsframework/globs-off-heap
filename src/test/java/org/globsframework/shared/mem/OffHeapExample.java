package org.globsframework.shared.mem;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.functional.FunctionalKeyBuilderFactory;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OffHeapExample {
    public static void main(String[] args) throws IOException {
        // 1. Define a GlobType
        GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("Person");
        GlobType personType = typeBuilder.unCompleteType();
        IntegerField id = typeBuilder.declareIntegerField("id");
        StringField name = typeBuilder.declareStringField("name");
        IntegerField age = typeBuilder.declareIntegerField("age");
        typeBuilder.complete();

        // 2. Create some data
        List<Glob> people = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            people.add(personType.instantiate()
                    .set(id, i)
                    .set(name, "Person " + i)
                    .set(age, 20 + (i % 50)));
        }

        // 3. Create an OffHeapService
        OffHeapService offHeapService = OffHeapService.create(personType);

        // 4. Define indexes
        FunctionalKeyBuilder idKeyBuilder = FunctionalKeyBuilderFactory.create(personType)
                .add(id)
                .create();
        OffHeapUniqueIndex idIndex = offHeapService.declareUniqueIndex("idIndex", idKeyBuilder);

        FunctionalKeyBuilder nameKeyBuilder = FunctionalKeyBuilderFactory.create(personType)
                .add(name)
                .create();
        OffHeapNotUniqueIndex nameIndex = offHeapService.declareNotUniqueIndex("nameIndex", nameKeyBuilder);

        // 5. Create a shared memory arena
        Arena arena = Arena.ofShared();

        // 6. Create a directory for storage
        Path storagePath = Path.of("/tmp/offheap-example");
        Files.createDirectories(storagePath);

        // 7. Write data to off-heap storage
        try (OffHeapWriteService writeService = offHeapService.createWrite(storagePath, arena)) {
            writeService.save(people);
        }

        // 8. Read data from off-heap storage
        try (OffHeapReadService readService = offHeapService.createRead(storagePath, arena)) {
            // 8.1 Get the unique index
            ReadOffHeapUniqueIndex readIdIndex = readService.getIndex(idIndex);

            // 8.2 Find a specific person by ID
            FunctionalKeyBuilder keyBuilder = FunctionalKeyBuilderFactory.create(personType)
                    .add(id)
                    .create();
            OffHeapRef ref = readIdIndex.find(keyBuilder.create().set(id, 42).create());
            Optional<Glob> person = readService.read(ref);
            person.ifPresent(p -> System.out.println("Found: " + p.get(name) + ", age: " + p.get(age)));

            // 8.3 Get the non-unique index
            ReadOffHeapMultiIndex readNameIndex = readService.getIndex(nameIndex);

            // 8.4 Find people by name
            OffHeapRefs refs = readNameIndex.find(keyBuilder.create().set(name, "Person 50").create());
            readService.read(refs, glob ->
                    System.out.println("ID: " + glob.get(id) + ", Name: " + glob.get(name) + ", Age: " + glob.get(age))
            );

            // 8.5 Read all data
            System.out.println("\nAll people:");
            readService.readAll(glob ->
                    System.out.println("ID: " + glob.get(id) + ", Name: " + glob.get(name) + ", Age: " + glob.get(age))
            );
        }
    }
}