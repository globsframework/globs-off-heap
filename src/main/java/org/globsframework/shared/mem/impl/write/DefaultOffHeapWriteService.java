package org.globsframework.shared.mem.impl.write;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetIntAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetLongAccessor;
import org.globsframework.shared.mem.OffHeapWriteService;
import org.globsframework.shared.mem.impl.DefaultOffHeapService;
import org.globsframework.shared.mem.impl.Index;
import org.globsframework.shared.mem.impl.IndexTypeBuilder;
import org.globsframework.shared.mem.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.impl.field.handleacces.HandleAccess;
import org.globsframework.shared.mem.model.HeapInline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class DefaultOffHeapWriteService implements OffHeapWriteService {
    private static final Logger logger = LoggerFactory.getLogger(DefaultOffHeapWriteService.class);
    private final Path path;
    private final GlobType dataType;
    private final Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap;
    private final Set<GlobType> typeToSave;
    private final Map<String, Index> index;

    public DefaultOffHeapWriteService(Path path, GlobType dataType, Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap,
                                      Set<GlobType> typeToSave, Map<String, Index> index) throws IOException {
        this.path = path;
        this.dataType = dataType;
        this.offHeapTypeInfoMap = offHeapTypeInfoMap;
        this.typeToSave = typeToSave;
        this.index = index;
    }

    @Override
    public void save(Collection<Glob> globs) throws IOException {
        final Map<GlobType, IdentityHashMap<Glob, Glob>> extracted = extractGlobToSave(globs);
        final Map<String, Glob> allStrings = getAndSaveVarStrings(globs);
        final Map<GlobType, IdentityHashMap<Glob, Long>> offsets = new HashMap<>();
        for (Map.Entry<GlobType, IdentityHashMap<Glob, Glob>> globTypeIdentityHashMapEntry : extracted.entrySet()) {
            int position = 0;
            final OffHeapTypeInfo offHeapTypeInfo = offHeapTypeInfoMap.get(globTypeIdentityHashMapEntry.getKey());
            if (offHeapTypeInfo == null) {
                throw new RuntimeException("Bug : no OffHeapTypeInfo found for type " + globTypeIdentityHashMapEntry.getKey());
            }
            for (Glob glob : globTypeIdentityHashMapEntry.getValue().keySet()) {
                offsets.computeIfAbsent(glob.getType(), type -> new IdentityHashMap<>())
                        .put(glob, position * offHeapTypeInfo.byteSizeWithPadding());
                position++;
            }
        }
        if (extracted.isEmpty()) {
            try (FileChannel _ = FileChannel.open(path.resolve(DefaultOffHeapService.createContentFileName(dataType)),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            }
            return;
        }

        for (Map.Entry<GlobType, IdentityHashMap<Glob, Glob>> globTypeIdentityHashMapEntry : extracted.entrySet()) {
            final OffHeapTypeInfo offHeapTypeInfo = offHeapTypeInfoMap.get(globTypeIdentityHashMapEntry.getKey());
            saveData(path.resolve(DefaultOffHeapService.createContentFileName(globTypeIdentityHashMapEntry.getKey())),
                    offHeapTypeInfo, offHeapTypeInfoMap,
                    globTypeIdentityHashMapEntry.getValue().keySet(), 1024 * 1024, allStrings, offsets);
        }

        for (Map.Entry<String, Index> entry : index.entrySet()) {
            final FunctionalKeyBuilder functionalKeyBuilder = entry.getValue().getKeyBuilder();
            final String indexName = entry.getKey();
            final Field[] keyFields = functionalKeyBuilder.getFields();
            if (entry.getValue().isUnique()) {
                computeUniqueIndex(globs, allStrings, keyFields, functionalKeyBuilder, indexName, offsets.get(dataType));
            } else {
                computeMultiIndex(globs, allStrings, keyFields, functionalKeyBuilder, indexName, offsets.get(dataType));
            }
        }
    }

    private Map<String, Glob> getAndSaveVarStrings(Collection<Glob> root) throws IOException {
        final Map<String, Glob> stringGlobMap = new HashMap<>();
        getStringGlobMap(root, stringGlobMap);
        return createStringsFile(path.resolve(DefaultOffHeapService.STRINGS_DATA), stringGlobMap);
    }

    private static Map<GlobType, IdentityHashMap<Glob, Glob>> extractGlobToSave(Collection<Glob> l1) {
        Map<GlobType, IdentityHashMap<Glob, Glob>> result = new HashMap<>();
        for (Glob glob : l1) {
            extractGlobToSave(glob, result);
            result.computeIfAbsent(glob.getType(), type -> new IdentityHashMap<>()).put(glob, glob);
        }
        return result;
    }

    private static void extractGlobToSave(Glob glob, Map<GlobType, IdentityHashMap<Glob, Glob>> result) {
        final Field[] fields = glob.getType().getFields();
        for (Field field : fields) {
            final boolean notNull = glob.isNotNull(field);
            if (notNull) {
                switch (field) {
                    case GlobField f -> {
                        Glob g = glob.get(f);
                        if (!f.hasAnnotation(HeapInline.UNIQUE_KEY)) {
                            result.computeIfAbsent(g.getType(), type -> new IdentityHashMap<>()).put(g, g);
                        }
                        extractGlobToSave(g, result);
                    }
                    case GlobUnionField f -> extractGlobToSave(glob.get(f), result);
                    case GlobArrayField f -> Stream.of(glob.get(f)).filter(Objects::nonNull).forEach(g -> {
                        if (!f.hasAnnotation(HeapInline.UNIQUE_KEY)) {
                            result.computeIfAbsent(g.getType(), type -> new IdentityHashMap<>()).put(g, g);
                        }
                        extractGlobToSave(g, result);
                    });
                    case GlobArrayUnionField f -> Stream.of(glob.get(f)).filter(Objects::nonNull).forEach(g -> {
                        if (!f.hasAnnotation(HeapInline.UNIQUE_KEY)) {
                            result.computeIfAbsent(g.getType(), type -> new IdentityHashMap<>()).put(g, g);
                        }
                        extractGlobToSave(g, result);
                    });
                    default -> {
                    }
                }
            }
        }
    }

    private void computeUniqueIndex(Collection<Glob> globs, Map<String, Glob> allStrings, Field[] keyFields, FunctionalKeyBuilder functionalKeyBuilder, String indexName, IdentityHashMap<Glob, Long> offsetPerData) throws IOException {

        //order keys
        TreeMap<FunctionalKey, Glob> indice = new TreeMap<>(new IndexFunctionalKeyComparator(keyFields));
        for (Glob glob : globs) {
            final FunctionalKey functionalKey = functionalKeyBuilder.create(glob);
            if (indice.put(functionalKey, glob) != null) {
                throw new RuntimeException("Duplicate key " + functionalKey + " for glob " + glob);
            }
        }

        final Set<Map.Entry<FunctionalKey, Glob>> entries = indice.descendingMap().reversed().entrySet();

        Map.Entry<FunctionalKey, Glob>[] orderedData = new Map.Entry[entries.size()];
        int i = 0;
        for (Map.Entry<FunctionalKey, Glob> functionalKeyGlobEntry : entries) {
            orderedData[i] = functionalKeyGlobEntry;
            ++i;
        }

        // create a tree by splitting the sorted array for use in binary search
        // the middle of the array is the root and will be the first elements in the index.
        Node1Elements root = Utils.split1Element(0, orderedData.length - 1);


        // give the index of the element to each elements
        // with that we will know the offset of the left and right elements of each element.
        AtomicInteger nbElement = new AtomicInteger(0);
        scan(root, node1Elements -> node1Elements.order = nbElement.getAndIncrement());

        // create the globType that represent an element :
        IndexTypeBuilder indexTypeBuilder = new IndexTypeBuilder(indexName, keyFields);

        List<Glob> indexGlobs = new ArrayList<>();
        final GlobSetLongAccessor dataOffset1Accessor = indexTypeBuilder.dataOffset1Accessor;
        final GlobSetIntAccessor dataLenOffset1Accessor = indexTypeBuilder.dataLenOffset1Accessor;
        final GlobSetIntAccessor offsetVal1Accessor = indexTypeBuilder.offsetVal1Accessor;
        final GlobSetIntAccessor offsetVal2Accessor = indexTypeBuilder.offsetVal2Accessor;
        // scan all the element to create the globs to be saved.
        scan(root, new UniqueIndexNode1ElementsConsumer(indexTypeBuilder, orderedData, dataLenOffset1Accessor, dataOffset1Accessor, offsetPerData,
                offsetVal1Accessor, offsetVal2Accessor, keyFields, indexGlobs));

        final HashMap<GlobType, IdentityHashMap<Glob, Long>> offsets = new HashMap<>();
        final IdentityHashMap<Glob, Long> offset = computeOffset(indexTypeBuilder, indexGlobs);
        offsets.put(indexTypeBuilder.offHeapIndexTypeInfo.type, offset);

        saveData(path.resolve(DefaultOffHeapService.getIndexNameFile(indexName)),
                indexTypeBuilder.offHeapIndexTypeInfo, offHeapTypeInfoMap, indexGlobs, 1000, allStrings, offsets);
    }

    record FunctionalKeyAndDataRef(FunctionalKey functionalKey, long dataArrayRefOrDataRef, int len) {
    }

    private void computeMultiIndex(Collection<Glob> globs, Map<String, Glob> allStrings, Field[] keyFields,
                                   FunctionalKeyBuilder functionalKeyBuilder, String indexName, IdentityHashMap<Glob, Long> offsetPerData) throws IOException {
        TreeMap<FunctionalKey, List<Glob>> indice = new TreeMap<>(new IndexFunctionalKeyComparator(keyFields));
        for (Glob glob : globs) {
            final FunctionalKey functionalKey = functionalKeyBuilder.create(glob);
            indice.compute(functionalKey, (functionalKey1, globs1) -> {
                if (globs1 == null) {
                    globs1 = new ArrayList<>(1);
                }
                globs1.add(glob);
                return globs1;
            });
        }

        final Set<Map.Entry<FunctionalKey, List<Glob>>> entries = indice.descendingMap().reversed().entrySet();

        Map.Entry<FunctionalKey, List<Glob>>[] orderedData = new Map.Entry[entries.size()];
        int i = 0;
        for (Map.Entry<FunctionalKey, List<Glob>> functionalKeyGlobEntry : entries) {
            orderedData[i] = functionalKeyGlobEntry;
            ++i;
        }
        FunctionalKeyAndDataRef[] offsetPerDataMap = new FunctionalKeyAndDataRef[orderedData.length];

        final Path pathToFile = path.resolve(DefaultOffHeapService.getIndexDataNameFile(indexName));
        final MemorySegment memorySegment;
        try (Arena arena = Arena.ofConfined()) {
            memorySegment = arena.allocate(100000);
            try (FileChannel open = FileChannel.open(pathToFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                long globalOffset = 0;
                long localOffset = 0;
                final ByteBuffer byteBuffer = memorySegment.asByteBuffer();
                final VarHandle varHandle = ValueLayout.JAVA_LONG.varHandle();
                for (int j = 0; j < orderedData.length; j++) {
                    Map.Entry<FunctionalKey, List<Glob>> orderedDatum = orderedData[j];
                    final List<Glob> value = orderedDatum.getValue();
                    if (value.size() > 1) {
                        if (localOffset + value.size() * 8L > memorySegment.byteSize()) {
                            byteBuffer.limit(Math.toIntExact(localOffset));
                            open.write(byteBuffer);
                            byteBuffer.clear();
                            localOffset = 0;
                            if (value.size() * 8L > memorySegment.byteSize()) {
                                throw new RuntimeException("Max size for data ref array is " + memorySegment.byteSize() + " bytes wanted size is " + value.size() * 8);
                            }
                        }
                        offsetPerDataMap[j] = new FunctionalKeyAndDataRef(orderedDatum.getKey(), globalOffset, value.size());
                        for (Glob glob : value) {
                            final Long index = offsetPerData.get(glob);
                            varHandle.set(memorySegment, localOffset, index);
                            localOffset += 8;
                        }
                        globalOffset += value.size() * 8L;
                    } else {
                        offsetPerDataMap[j] = new FunctionalKeyAndDataRef(orderedDatum.getKey(), offsetPerData.get(value.getFirst()), value.size());
                    }
                }

                byteBuffer.limit(Math.toIntExact(localOffset));
                open.write(byteBuffer);
                byteBuffer.clear();
            }
        }

        Node1Elements root = Utils.split1Element(0, orderedData.length - 1);
        AtomicInteger nbElement = new AtomicInteger(0);
        scan(root, node1Elements -> node1Elements.order = nbElement.getAndIncrement());

        IndexTypeBuilder indexTypeBuilder = new IndexTypeBuilder(indexName, keyFields);

        List<Glob> indexGlobs = new ArrayList<>();
        final GlobSetLongAccessor dataOffset1Accessor = indexTypeBuilder.dataOffset1Accessor;
        final GlobSetIntAccessor dataLenOffset1Accessor = indexTypeBuilder.dataLenOffset1Accessor;
        final GlobSetIntAccessor offsetVal1Accessor = indexTypeBuilder.offsetVal1Accessor;
        final GlobSetIntAccessor offsetVal2Accessor = indexTypeBuilder.offsetVal2Accessor;

        scan(root, new MultiIndexNode1ElementsConsumer(indexTypeBuilder, offsetPerDataMap, dataLenOffset1Accessor, dataOffset1Accessor,
                offsetVal1Accessor, offsetVal2Accessor, keyFields, indexGlobs));
        final HashMap<GlobType, IdentityHashMap<Glob, Long>> offsets = new HashMap<>();
        final IdentityHashMap<Glob, Long> offset = computeOffset(indexTypeBuilder, indexGlobs);
        offsets.put(indexTypeBuilder.offHeapIndexTypeInfo.type, offset);

        saveData(path.resolve(DefaultOffHeapService.getIndexNameFile(indexName)), indexTypeBuilder.offHeapIndexTypeInfo,
                offHeapTypeInfoMap, indexGlobs, 1000, allStrings, offsets);
    }

    private static IdentityHashMap<Glob, Long> computeOffset(IndexTypeBuilder indexTypeBuilder, List<Glob> indexGlobs) {
        int index = 0;
        final long sizeWithPadding = indexTypeBuilder.offHeapIndexTypeInfo.byteSizeWithPadding();
        final IdentityHashMap<Glob, Long> offset = new IdentityHashMap<>();
        for (Glob indexGlob : indexGlobs) {
            offset.put(indexGlob, index * sizeWithPadding);
            index++;
        }
        return offset;
    }

    private void scan(Node1Elements node, Consumer<Node1Elements> consumer) {
        if (node != null) {
            consumer.accept(node);
            scan(node.val1, consumer);
            scan(node.val2, consumer);
        }
    }

    static Map<String, Glob> createStringsFile(Path pathToFile, Map<String, Glob> allStrings) throws IOException {

        try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(pathToFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            int offset = 0;
            for (Map.Entry<String, Glob> entry : allStrings.entrySet()) {
                final byte[] bytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                final MutableGlob value = (MutableGlob) entry.getValue();
                value.set(StringRefType.len, bytes.length);
                value.set(StringRefType.offset, offset);
                stream.write(bytes);
                offset += bytes.length;
            }
        }
        return allStrings;
    }

    record FieldToScan(StringField[] stringFields, GlobField[] globFields, GlobArrayField[] globArrayFields,
                       GlobUnionField[] globUnionFields, GlobArrayUnionField[] globArrayUnionFields) {
    }

    private static void getStringGlobMap(Collection<Glob> globs, Map<String, Glob> allStrings) {
        Map<GlobType, FieldToScan> stringFieldsMap = new HashMap<>();
        int index = 0;
        for (Glob glob : globs) {
            index = scanForString(allStrings, stringFieldsMap, glob, index);
        }
    }

    private static int scanForString(Map<String, Glob> allStrings, Map<GlobType, FieldToScan> stringFieldsMap, Glob glob, int index) {
        FieldToScan fieldToScan = stringFieldsMap.computeIfAbsent(glob.getType(),
               globType -> new FieldToScan(globType.streamFields().filter(field -> field instanceof StringField).toArray(StringField[]::new),
                       globType.streamFields().filter(field -> field instanceof GlobField).toArray(GlobField[]::new),
                       globType.streamFields().filter(field -> field instanceof GlobArrayField).toArray(GlobArrayField[]::new),
                       globType.streamFields().filter(field -> field instanceof GlobUnionField).toArray(GlobUnionField[]::new),
                       globType.streamFields().filter(field -> field instanceof GlobArrayUnionField).toArray(GlobArrayUnionField[]::new)
               ));
        for (StringField stringField : fieldToScan.stringFields()) {
            final String key = glob.get(stringField);
            if (key != null && !allStrings.containsKey(key)) {
                allStrings.put(glob.get(stringField), StringRefType.TYPE.instantiate()
                        .set(StringRefType.id, ++index));
            }
        }
        for (GlobField globField : fieldToScan.globFields) {
            final Glob g = glob.get(globField);
            if (g != null) {
                index = scanForString(allStrings, stringFieldsMap, g, index);
            }
        }
        for (GlobUnionField globField : fieldToScan.globUnionFields) {
            final Glob g = glob.get(globField);
            if (g != null) {
                index = scanForString(allStrings, stringFieldsMap, g, index);
            }
        }
        for (GlobArrayField field : fieldToScan.globArrayFields) {
            for (Glob g : glob.getOrEmpty(field)) {
                if (g != null) {
                    index = scanForString(allStrings, stringFieldsMap, g, index);
                }
            }
        }
        for (GlobArrayUnionField field : fieldToScan.globArrayUnionFields) {
            for (Glob g : glob.getOrEmpty(field)) {
                if (g != null) {
                    index = scanForString(allStrings, stringFieldsMap, g, index);
                }
            }
        }
        return index;
    }

    private static void saveData(Path pathToFile, OffHeapTypeInfo offHeapTypeInfo,
                                 Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap, Collection<Glob> globs, int bufferSize, Map<String, Glob> allStrings,
                                 Map<GlobType, IdentityHashMap<Glob, Long>> offsets) throws IOException {
        final MemorySegment memorySegment;
        final IdentityHashMap<Glob, Long> offsetToCheck = offsets.get(offHeapTypeInfo.type);
        try (Arena arena = Arena.ofConfined()) {
            memorySegment = arena.allocate(bufferSize);

            try (FileChannel open = FileChannel.open(pathToFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                final FreeOffset freeOffset = new FreeOffset();
                final SaveContext saveContext = new SaveContext(offsets, offHeapTypeInfoMap, allStrings::get, freeOffset, new Flush() {
                    final ByteBuffer byteBuffer = memorySegment.asByteBuffer();

                    @Override
                    public void flush() throws IOException {
                        if (freeOffset.memorySegmentFreeOffset == 0) {
                            return;
                        }
                        byteBuffer.limit(Math.toIntExact(freeOffset.memorySegmentFreeOffset));
                        open.write(byteBuffer);
                        byteBuffer.clear();
                        freeOffset.memorySegmentFreeOffset = 0;
                    }
                });

                final long groupSize = offHeapTypeInfo.byteSizeWithPadding();
                for (Glob glob : globs) {
                    if (offHeapTypeInfo.type != glob.getType()) {
                        final String s = "Bad type " + offHeapTypeInfo.type.getName() + " but got " + glob.getType().getName();
                        logger.error(s);
                        throw new RemoteException(s);
                    }
                    final long offset = saveContext.freeOffset().globalFreeOffset;
                    if (offsetToCheck != null && offsetToCheck.get(glob) != offset) {
                        throw new RuntimeException("Offset mismatch " + offsetToCheck.get(glob) + " != " + offset + " for " +
                                                   offHeapTypeInfo.type.getName() + " and data : " + glob);
                    }
                    saveGlob(offHeapTypeInfo, bufferSize, glob, saveContext, groupSize, memorySegment);
                }
                saveContext.flush().flush();
            }
        }
    }

    private static void saveGlob(OffHeapTypeInfo offHeapTypeInfo1, int bufferSize, Glob glob, SaveContext saveContext,
                                 long groupSize, MemorySegment memorySegment) throws IOException {
        if (groupSize >= bufferSize) {
            throw new RuntimeException("not enough bytes for group layout " + groupSize + " vs available " + bufferSize);
        }
        saveContext.freeOffset().globalFreeOffset += groupSize;
        if (saveContext.freeOffset().memorySegmentFreeOffset + groupSize >= bufferSize) {
            saveContext.flush().flush();
        }
        final long currenOffset = saveContext.freeOffset().memorySegmentFreeOffset;
        for (HandleAccess handleAccess : offHeapTypeInfo1.handleAccesses) {
            handleAccess.save(glob, memorySegment, currenOffset, saveContext);
        }
        saveContext.freeOffset().memorySegmentFreeOffset += groupSize;
        //callback other save.
    }

    @Override
    public void close() throws IOException {
    }

    private static class UniqueIndexNode1ElementsConsumer implements Consumer<Node1Elements> {
        private final IndexTypeBuilder indexTypeBuilder;
        private final Map.Entry<FunctionalKey, Glob>[] orderedData;
        private final GlobSetIntAccessor dataLenOffset1Accessor;
        private final GlobSetLongAccessor dataOffset1Accessor;
        private final IdentityHashMap<Glob, Long> offsetPerData;
        private final GlobSetIntAccessor offsetVal1Accessor;
        private final GlobSetIntAccessor offsetVal2Accessor;
        private final Field[] keyFields;
        private final List<Glob> indexGlobs;

        public UniqueIndexNode1ElementsConsumer(IndexTypeBuilder indexTypeBuilder, Map.Entry<FunctionalKey, Glob>[] orderedData,
                                                GlobSetIntAccessor dataLenOffset1Accessor, GlobSetLongAccessor dataOffset1Accessor, IdentityHashMap<Glob, Long> offsetPerData,
                                                GlobSetIntAccessor offsetVal1Accessor, GlobSetIntAccessor offsetVal2Accessor, Field[] keyFields,
                                                List<Glob> indexGlobs) {
            this.indexTypeBuilder = indexTypeBuilder;
            this.orderedData = orderedData;
            this.dataLenOffset1Accessor = dataLenOffset1Accessor;
            this.dataOffset1Accessor = dataOffset1Accessor;
            this.offsetPerData = offsetPerData;
            this.offsetVal1Accessor = offsetVal1Accessor;
            this.offsetVal2Accessor = offsetVal2Accessor;
            this.keyFields = keyFields;
            this.indexGlobs = indexGlobs;
        }

        @Override
        public void accept(Node1Elements node1Elements) {
            final MutableGlob index = indexTypeBuilder.indexType.instantiate();
            final Map.Entry<FunctionalKey, Glob> currentData = orderedData[node1Elements.indice1];
            dataOffset1Accessor.setNative(index, offsetPerData.get(currentData.getValue()));
            dataLenOffset1Accessor.setNative(index, 1);

            offsetVal1Accessor.setNative(index, node1Elements.val1 != null ? node1Elements.val1.order : -1);
            offsetVal2Accessor.setNative(index, node1Elements.val2 != null ? node1Elements.val2.order : -1);
            for (int j = 0; j < keyFields.length; j++) {
                index.setValue(indexTypeBuilder.indexFields[j], currentData.getKey().getValue(keyFields[j]));
            }
            indexGlobs.add(index);
        }
    }

    private static class MultiIndexNode1ElementsConsumer implements Consumer<Node1Elements> {
        private final IndexTypeBuilder indexTypeBuilder;
        private final FunctionalKeyAndDataRef[] orderedDataToDataRefOrDataRef;
        private final GlobSetIntAccessor dataLenOffset1Accessor;
        private final GlobSetLongAccessor dataOffset1Accessor;
        private final GlobSetIntAccessor offsetVal1Accessor;
        private final GlobSetIntAccessor offsetVal2Accessor;
        private final Field[] keyFields;
        private final List<Glob> outIndexGlobs;

        public MultiIndexNode1ElementsConsumer(IndexTypeBuilder indexTypeBuilder,
                                               FunctionalKeyAndDataRef[] orderedDataToDataRefOrDataRef,
                                               GlobSetIntAccessor dataLenOffset1Accessor,
                                               GlobSetLongAccessor dataOffset1Accessor,
                                               GlobSetIntAccessor offsetVal1Accessor,
                                               GlobSetIntAccessor offsetVal2Accessor,
                                               Field[] keyFields,
                                               List<Glob> outIndexGlobs) {
            this.indexTypeBuilder = indexTypeBuilder;
            this.orderedDataToDataRefOrDataRef = orderedDataToDataRefOrDataRef;
            this.dataLenOffset1Accessor = dataLenOffset1Accessor;
            this.dataOffset1Accessor = dataOffset1Accessor;
            this.offsetVal1Accessor = offsetVal1Accessor;
            this.offsetVal2Accessor = offsetVal2Accessor;
            this.keyFields = keyFields;
            this.outIndexGlobs = outIndexGlobs;
        }

        @Override
        public void accept(Node1Elements node1Elements) {
            final MutableGlob index = indexTypeBuilder.indexType.instantiate();
            final FunctionalKeyAndDataRef currentData = orderedDataToDataRefOrDataRef[node1Elements.indice1];
            dataLenOffset1Accessor.setNative(index, currentData.len());
            dataOffset1Accessor.setNative(index, currentData.dataArrayRefOrDataRef()); // if len == 1 => ref of data else ref of array of data ref
            offsetVal1Accessor.setNative(index, node1Elements.val1 != null ? node1Elements.val1.order : -1);
            offsetVal2Accessor.setNative(index, node1Elements.val2 != null ? node1Elements.val2.order : -1);
            for (int j = 0; j < keyFields.length; j++) {
                index.setValue(
                        indexTypeBuilder.indexFields[j],
                        currentData.functionalKey().getValue(keyFields[j]));
            }
            outIndexGlobs.add(index);
        }
    }

}
