# Globs Off-Heap

Store [Glob](https://globsframework.org) objects in memory-mapped files outside the Java heap, and read them
back through indexed lookups.

Data is written as flat, fixed-stride records addressed by byte offset. The record layout is derived once
from the `GlobType` with the Foreign Memory API (`java.lang.foreign.GroupLayout`), and every field access
goes through a pre-resolved `VarHandle` — no reflection, and nothing is copied on read beyond the returned
`Glob` itself.

- Reduced GC pressure: the data lives in mapped files, not on the heap
- Persistent by construction: the storage format *is* the file format
- Two storage engines: a write-once **tree** store with sorted indexes, and a **hash** store supporting
  in-place update

## Requirements

- Java 22 or higher (the Foreign Memory API is used without preview flags)
- `org.globsframework:globs` 5.6.0

## Installation

```xml
<dependency>
    <groupId>org.globsframework</groupId>
    <artifactId>globs-off-heap</artifactId>
    <version>5.7.0</version>
</dependency>
```

The artifact is `globs-off-heap`, published from the `globs-off-heap` repository.

## Tree store — write once, search by index

`OffHeapTreeService` writes a whole collection in one pass and then serves reads through indexes declared
before the write. There is no update: to change the data, save the collection again.

```java
// 1. Define a GlobType
GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("Person");
IntegerField id = typeBuilder.declareIntegerField("id");
StringField name = typeBuilder.declareStringField("name");
IntegerField age = typeBuilder.declareIntegerField("age");
GlobType personType = typeBuilder.build();

// 2. Create some data
List<Glob> people = new ArrayList<>();
for (int i = 0; i < 1000; i++) {
    people.add(personType.instantiate()
            .set(id, i)
            .set(name, "Person " + i)
            .set(age, 20 + (i % 50)));
}

// 3. Create the service and declare the indexes (before writing)
OffHeapTreeService offHeapService = OffHeapTreeService.create(personType);

FunctionalKeyBuilder idKeyBuilder = FunctionalKeyBuilderFactory.create(personType)
        .add(id)
        .create();
OffHeapUniqueIndex idIndex = offHeapService.declareUniqueIndex("idIndex", idKeyBuilder);

FunctionalKeyBuilder nameKeyBuilder = FunctionalKeyBuilderFactory.create(personType)
        .add(name)
        .create();
OffHeapNotUniqueIndex nameIndex = offHeapService.declareNotUniqueIndex("nameIndex", nameKeyBuilder);

Path storagePath = Path.of("/tmp/offheap-example");
Files.createDirectories(storagePath);

// 4. Write
try (OffHeapWriteTreeService writeService = offHeapService.createWrite(storagePath)) {
    writeService.save(people);
}

// 5. Read — the caller owns the Arena and must keep it open while reading
Arena arena = Arena.ofShared();
try (OffHeapReadTreeService readService = offHeapService.createRead(storagePath, arena)) {
    // unique index: one ref, or OffHeapRef.NULL
    ReadOffHeapUniqueIndex readIdIndex = readService.getIndex(idIndex);
    OffHeapRef ref = readIdIndex.find(idKeyBuilder.create().set(id, 42).create());
    Glob person = readService.read(ref);
    System.out.println("Found: " + person.get(name) + ", age: " + person.get(age));

    // non-unique index: a set of refs
    ReadOffHeapMultiIndex readNameIndex = readService.getIndex(nameIndex);
    OffHeapRefs refs = readNameIndex.find(nameKeyBuilder.create().set(name, "Person 50").create());
    readService.read(refs, glob -> {
        System.out.println("ID: " + glob.get(id) + ", Age: " + glob.get(age));
        return true;   // the boolean is only honoured by warmup(), not by read/readAll
    });

    // full scan
    readService.readAll(glob -> {
        System.out.println("ID: " + glob.get(id) + ", Name: " + glob.get(name));
        return true;
    });
}
```

This example is kept compiling as `OffHeapExample` in the test sources.

### find vs. search

- `find(key)` requires every field of the index key to be set; it returns the exact matches.
- `search(key)` matches on a *prefix* of the index key — set only the leading fields and it returns every
  record under that prefix. Fields set past the prefix are applied as an extra filter.

Both are a descent over a balanced tree flattened into the index file, comparing key values directly in the
mapped segment; no `Glob` is materialized for records that do not match.

### Reading a subset of fields

`readAll(consumer, onlyFields)` and `read(offset, onlyFields)` take a `Predicate<Field>` and only decode the
fields that pass it — worth using when the type is wide and the query touches a few columns. `warmup(...)`
touches random records to page the mapping in before timing anything.

## Hash store — update in place

`OffHeapHashService` writes a hash table (collision chaining, entries chained into an overflow area after the
buckets) alongside the data, and supports updating records after the fact without rewriting the files.

```java
OffHeapHashService hashService = OffHeapHashService.create(personType);

FunctionalKeyBuilder keyBuilder = FunctionalKeyBuilderFactory.create(personType)
        .add(id)
        .create();
// the size is the wanted table size; it is rounded up to a power of two
hashService.declare("id", keyBuilder, 20_000);

Path storagePath = Files.createTempDirectory("offheap-hash");
hashService.createWriter(storagePath).save(people);

Arena arena = Arena.ofShared();
OffHeapReadHashService readService =
        hashService.createReader(storagePath, arena, GlobType::instantiate);
OffHeapHashAccess reader = readService.getReader("id");

Glob found = reader.get(keyBuilder.create().set(id, 42).create());
reader.readAll(glob -> true);

// update: writes the new record into a free slot, repoints the index, frees the old slot
OffHeapUpdaterService updater = hashService.createUpdater(storagePath, Arena.ofShared());
updater.update(personType.instantiate().set(id, 42).set(name, "New name").set(age, 30));
```

Notes on updating:

- Records carry a hidden leading `long` slot used as a free marker. When a record is replaced, its old slot
  (and the slots of the sub-globs it referenced) are stamped with the current time and only become reusable
  about a second later, so a reader that is mid-read on the old record is not overwritten under it.
- The writer reserves a fixed amount of headroom: 100 spare records per data file, 100 spare index buckets,
  and roughly double the string file. Beyond that, `update` fails with *No free space* / *No more space for
  strings. Growth not yet supported.* Size the initial write accordingly.
- `update` is `synchronized` on the updater; the returned `int` is not meaningful yet (always 0).
- The updater maps the files read-write, the reader read-only; a reader created before an update sees the
  new values through its own mapping.

## Supported field types

| Field | Storage |
| --- | --- |
| `BooleanField`, `IntegerField`, `LongField`, `DoubleField` | inline, natural size, padded to alignment |
| `DateField` | inline `long` |
| `DateTimeField` | inline: date + time + nanos + a 52-byte zone id |
| `StringField` | length + address into a shared `strings.data`; each distinct string stored once |
| `StringField` with `@MaxSize` | inline `char[maxSize]` (or `byte[maxSize]` with `Heap7BitsString`) |
| `IntegerArrayField` with `@ArraySize` | inline length + fixed-size `int[]` |
| `GlobField` | 8-byte offset into the target type's own file, or inline with `HeapInline` |
| `GlobArrayField` | length + fixed-size array of offsets, or of inline records with `HeapInline` |

Union fields (`GlobUnionField`, `GlobArrayUnionField`) are not supported and are rejected at layout time.

`isSet` and `isNull` round-trip for every kind above, except `@MaxSize` strings where an unset value reads
back as null.

## Layout annotations

These live in `org.globsframework.shared.mem.model` and follow the framework's Glob-annotation convention —
pass `X.UNIQUE_GLOB` (or `X.create(...)`) when declaring the field.

- **`HeapMaxElement.create(n)`** — **required** on every `GlobArrayField`: fixes the number of slots reserved
  in the record. A layout cannot be built without it.
- **`HeapInline.UNIQUE_GLOB`** — on a `GlobField`/`GlobArrayField`, embeds the target record inside the parent
  instead of writing it to its own file and referencing it by offset. Fewer indirections on read; the parent
  record grows by the size of the target.
- **`Heap7BitsString.UNIQUE_GLOB`** — with `@MaxSize`, stores the string one byte per character instead of
  two. Only valid for characters that fit in 7 bits.

Core annotations `@MaxSize` (with its `allow_truncate` flag — without it, an over-long value throws on save)
and `@ArraySize` are also read.

```java
subObject = builder.declareGlobField("subObject", () -> SubType.TYPE, HeapInline.UNIQUE_GLOB);
children  = builder.declareGlobArrayField("children", () -> SubType.TYPE,
                                          HeapInline.UNIQUE_GLOB, HeapMaxElement.create(3));
label     = builder.declareStringField("label", MaxSize.create(15), Heap7BitsString.UNIQUE_GLOB);
```

## Files written in a storage directory

| File | Content |
| --- | --- |
| `content.data_<TypeName>` | the records of one type, at a fixed stride |
| `content.data_HashHeader<indexName>` | hash table buckets, one file per declared index (hash store only) |
| `strings.data` | `[int length][utf-8 bytes]` for every distinct variable-size string |
| `<indexName>Unique.data` | index records: key fields + data offset + child node numbers |
| `<indexName>Many.data` | for a non-unique index, the arrays of data offsets per key |

A directory written by one service is read back by a service built from the same `GlobType` and the same
index declarations. There is no schema header: changing the type changes the layout, and old files are not
readable by the new layout.

## Arenas and lifetime

The caller creates the `Arena` and passes it to `createRead` / `createReader` / `createUpdater`. The services
map the files into that arena and never close it. Every `Glob` read out of the store may reference the mapped
segment (strings, sub-globs are read eagerly, but the service itself is bound to the arena), so closing the
arena — or letting a confined arena's owning thread die — invalidates further reads. Use `Arena.ofShared()`
when several threads read the same store.

Reads are thread-safe once the service is built. Writes are not: one writer, no concurrent readers on the
files being written.

## Building

```bash
mvn test                      # full suite (JUnit 5)
mvn test -Dtest=SearchTest    # a single class
```

`FindPerf` in the test sources is a JMH benchmark of the unique-index lookup; it has no `main`, run it from
an IDE or through `org.openjdk.jmh.Main` with the test classpath.

## License

Apache License 2.0 — see <https://www.apache.org/licenses/LICENSE-2.0.txt>.

## Links

- [Globs Framework](https://globsframework.org)
- [GitHub repository](https://github.com/globsframework/globs-off-heap)
