# Globs Off-Heap

A high-performance Java library for storing and retrieving [Glob](https://globsframework.org) objects in off-heap memory with efficient indexing capabilities.

## Overview

Globs Off-Heap is an extension to the Globs framework that provides efficient off-heap memory storage for Glob objects. It leverages Java's Foreign Memory API (introduced in Java 22) to manage memory outside the JVM heap, offering several advantages:

- Reduced garbage collection pressure
- Improved memory efficiency for large datasets
- Persistent storage capabilities
- Fast access through indexed lookups

This library is particularly useful for applications that need to manage large amounts of structured data with high performance requirements.

## Features

- **Off-heap storage**: Store Glob objects in memory outside the Java heap
- **Indexing**: Create unique and non-unique indexes for fast data retrieval
- **Persistence**: Save and load data to/from disk
- **Memory efficiency**: Optimized memory layout for different field types
- **Type safety**: Maintains the type safety of the Globs framework
- **Low GC overhead**: Minimizes garbage collection impact for large datasets

## Requirements

- Java 22 or higher
- Globs framework 4.2.0 or higher

## Installation

Add the following dependency to your Maven project:

```xml
<dependency>
    <groupId>org.globsframework</groupId>
    <artifactId>globs-off-heap</artifactId>
    <version>4.3.0</version>
</dependency>
```

## Usage Example

Here's a basic example of how to use the library:

```java
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
        try (OffHeapWriteService writeService = offHeapService.createWrite(storagePath)) {
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
```

## API Overview

### Main Interfaces

- **OffHeapService**: Entry point for creating off-heap storage services
- **OffHeapWriteService**: Writes Glob objects to off-heap memory
- **OffHeapReadService**: Reads Glob objects from off-heap memory
- **OffHeapUniqueIndex**: Defines a unique index for fast lookups
- **OffHeapNotUniqueIndex**: Defines a non-unique index for lookups that may return multiple results
- **ReadOffHeapUniqueIndex**: Interface for querying a unique index
- **ReadOffHeapMultiIndex**: Interface for querying a non-unique index

### Key Concepts

1. **GlobType**: Defines the structure of your data
2. **FunctionalKey**: Used to define indexes and perform lookups
3. **Arena**: Manages the lifecycle of off-heap memory
4. **OffHeapRef/OffHeapRefs**: References to off-heap data

## Performance Considerations

- Use indexes for fields that will be frequently queried
- Close services properly to avoid memory leaks
- Consider the memory requirements of your application when sizing the off-heap storage

## License

This project is licensed under the terms of the MIT license.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Links

- [Globs Framework](https://globsframework.org)
- [GitHub Repository](https://github.com/globsframework/globs-ffm)

## validated AI generated README