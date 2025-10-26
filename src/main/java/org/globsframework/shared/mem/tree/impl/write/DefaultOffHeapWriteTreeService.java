package org.globsframework.shared.mem.tree.impl.write;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.model.globaccessor.set.GlobSetIntAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetLongAccessor;
import org.globsframework.shared.mem.DataSaver;
import org.globsframework.shared.mem.tree.OffHeapWriteTreeService;
import org.globsframework.shared.mem.tree.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DefaultOffHeapWriteTreeService implements OffHeapWriteTreeService {
    private static final Logger logger = LoggerFactory.getLogger(DefaultOffHeapWriteTreeService.class);
    private final Path path;
    private final GlobType dataType;
    private final Map<GlobType, RootOffHeapTypeInfo> offHeapTypeInfoMap;
    private final Set<GlobType> typeToSave;
    private final Map<String, Index> index;

    public DefaultOffHeapWriteTreeService(Path path, GlobType dataType, Map<GlobType, RootOffHeapTypeInfo> offHeapTypeInfoMap,
                                          Set<GlobType> typeToSave, Map<String, Index> index) throws IOException {
        this.path = path;
        this.dataType = dataType;
        this.offHeapTypeInfoMap = offHeapTypeInfoMap;
        this.typeToSave = typeToSave;
        this.index = index;
    }

    @Override
    public void save(Collection<Glob> globs) throws IOException {
        DataSaver dataSaver = new DataSaver(path, dataType, offHeapTypeInfoMap::get);
        final DataSaver.Result result = dataSaver.saveData(globs);

        for (Map.Entry<String, Index> entry : index.entrySet()) {
            final FunctionalKeyBuilder functionalKeyBuilder = entry.getValue().getKeyBuilder();
            final String indexName = entry.getKey();
            final Field[] keyFields = functionalKeyBuilder.getFields();
            if (entry.getValue().isUnique()) {
                computeUniqueIndex(globs, result.allStrings(), keyFields, functionalKeyBuilder, indexName, result.offsets().get(dataType));
            } else {
                computeMultiIndex(globs, result.allStrings(), keyFields, functionalKeyBuilder, indexName, result.offsets().get(dataType));
            }
        }
    }

    private void computeUniqueIndex(Collection<Glob> globs, Map<String, Glob> allStrings, Field[] keyFields,
                                    FunctionalKeyBuilder functionalKeyBuilder, String indexName, IdentityHashMap<Glob, Long> offsetPerData) throws IOException {

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
        offsets.put(indexTypeBuilder.offHeapIndexTypeInfo.primary().type, offset);

        DataSaver.saveData(path.resolve(DefaultOffHeapTreeService.getIndexNameFile(indexName)),
                indexTypeBuilder.offHeapIndexTypeInfo, indexGlobs, 1000, allStrings, offsets);
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

        final Path pathToFile = path.resolve(DefaultOffHeapTreeService.getIndexDataNameFile(indexName));
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
        offsets.put(indexTypeBuilder.offHeapIndexTypeInfo.primary().type, offset);

        DataSaver.saveData(path.resolve(DefaultOffHeapTreeService.getIndexNameFile(indexName)), indexTypeBuilder.offHeapIndexTypeInfo,
                indexGlobs, 1000, allStrings, offsets);
    }

    private static IdentityHashMap<Glob, Long> computeOffset(IndexTypeBuilder indexTypeBuilder, List<Glob> indexGlobs) {
        int index = 0;
        final long sizeWithPadding = indexTypeBuilder.offHeapIndexTypeInfo.primary().byteSizeWithPadding();
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
