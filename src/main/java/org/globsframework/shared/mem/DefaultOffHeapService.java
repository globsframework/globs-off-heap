package org.globsframework.shared.mem;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.core.utils.Utils;
import org.globsframework.core.utils.collections.IntHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.*;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DefaultOffHeapService implements OffHeapService {
    private static final Logger log = LoggerFactory.getLogger(DefaultOffHeapService.class);
    public static final String SUFFIX_ADDR = "_addr";
    public static final String SUFFIX_LEN = "_len";
    public static final String CONTENT_DATA = "content.data";
    public static final String STRINGS_DATA = "strings.data";
    private final OffHeapTypeInfo offHeapTypeInfo;
    private final Map<String, OffHeapIndex> index = new HashMap<>();

    public DefaultOffHeapService(GlobType type) {
        offHeapTypeInfo = new OffHeapTypeInfo(type);
    }

    @Override
    public OffHeapIndex declareIndex(String name, FunctionalKeyBuilder functionalKeyBuilder) {
        final DefaultOffHeapIndex value = new DefaultOffHeapIndex(name, functionalKeyBuilder);
        index.put(name, value);
        return value;
    }

    @Override
    public OffHeapWriteService createWrite(Path file, Arena arena) throws IOException {
        return new DefaultOffHeapWriteService(file, arena, offHeapTypeInfo, index);
    }

    @Override
    public OffHeapReadService createRead(Path directory, Arena arena) throws IOException {
        return new DefaultOffHeapReadService(directory, arena, offHeapTypeInfo, index);
    }

    private static class GroupLayoutAbstractFieldVisitor extends FieldVisitor.AbstractFieldVisitor {
        List<MemoryLayout> fieldsLayout = new ArrayList<>();

        public GroupLayoutAbstractFieldVisitor() {
        }

        @Override
        public void visitString(StringField field) throws Exception {
            fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + SUFFIX_ADDR));
            fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + SUFFIX_LEN));
        }

        @Override
        public void visitInteger(IntegerField field) {
            fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName()));
        }

        @Override
        public void visitDouble(DoubleField field) {
            fieldsLayout.add(ValueLayout.JAVA_DOUBLE.withName(field.getName()));
        }

        public GroupLayout createGroupLayout() {
            return MemoryLayout.structLayout(fieldsLayout.toArray(new MemoryLayout[0]));
        }
    }

    private static class DefaultOffHeapWriteService implements OffHeapWriteService {
        public static final int MIN_SIZE = 10000;
        private final Path path;
        private final OffHeapTypeInfo offHeapTypeInfo;
        private final Map<String, OffHeapIndex> index;

        public DefaultOffHeapWriteService(Path path, Arena arena, OffHeapTypeInfo offHeapTypeInfo, Map<String, OffHeapIndex> index) throws IOException {
            this.path = path;
            this.offHeapTypeInfo = offHeapTypeInfo;
            this.index = index;
        }

        @Override
        public OffHeapIndex declareIndex(FunctionalKeyBuilder functionalKeyBuilder) {
            return null;
        }

        @Override
        public void save(Collection<Glob> globs) throws IOException {
            final Map<String, Glob> allStrings = createStringsFile(globs, offHeapTypeInfo, path.resolve(STRINGS_DATA));

            final IdentityHashMap<Glob, Integer> offsetPerData =
                    saveData(path.resolve(CONTENT_DATA), offHeapTypeInfo, globs, MIN_SIZE, allStrings);

            for (Map.Entry<String, OffHeapIndex> entry : index.entrySet()) {
                final FunctionalKeyBuilder functionalKeyBuilder = entry.getValue().getKeyBuilder();
                final String indexName = entry.getKey();
                final Field[] keyFields = functionalKeyBuilder.getFields();
                TreeMap<FunctionalKey, Glob> indice = new TreeMap<>(new IndexFunctionalKeyComparator(allStrings,
                        keyFields));
                for (Glob glob : globs) {
                    final FunctionalKey functionalKey = functionalKeyBuilder.create(glob);
                    indice.put(functionalKey, glob);
                }

                final Set<Map.Entry<FunctionalKey, Glob>> entries = indice.descendingMap().reversed().entrySet();

                Map.Entry<FunctionalKey, Glob>[] orderedData = new Map.Entry[entries.size()];
                int i = 0;
                for (Map.Entry<FunctionalKey, Glob> functionalKeyGlobEntry : entries) {
                    orderedData[i] = functionalKeyGlobEntry;
                    ++i;
                }

                Node1Elements root = org.globsframework.shared.mem.Utils.split1Element(0, orderedData.length - 1);
                AtomicInteger nbElement = new AtomicInteger(0);
                scan(root, node1Elements -> node1Elements.order = nbElement.getAndIncrement());

                IndexTypeBuilder indexTypeBuilder = new IndexTypeBuilder(indexName, keyFields);

                List<Glob> indexGlobs = new ArrayList<>();
                final long groupSize = indexTypeBuilder.offHeapIndexTypeInfo.groupLayout.byteSize();
                scan(root, node1Elements -> {
                    final MutableGlob index = indexTypeBuilder.indexType.instantiate();
                    final Map.Entry<FunctionalKey, Glob> currentData = orderedData[node1Elements.indice1];
                    index.set(indexTypeBuilder.dataOffset1, offsetPerData.get(currentData.getValue()));
                    index.set(indexTypeBuilder.offsetVal1, node1Elements.val1 != null ? node1Elements.val1.order : -1);
                    index.set(indexTypeBuilder.offsetVal2, node1Elements.val2 != null ? node1Elements.val2.order : -1);
                    for (int j = 0; j < keyFields.length; j++) {
                        Field field = keyFields[j];
                        if (field instanceof StringField strField) {
                            final Glob strDesc = allStrings.get(currentData.getKey().get(strField));
                            index.set(indexTypeBuilder.strArr[j], strDesc != null ? strDesc.get(StringRefType.offset) : -1);
                            index.set(indexTypeBuilder.strLen[j], strDesc != null ? strDesc.get(StringRefType.len) : -1);
                        } else {
                            index.setValue(indexTypeBuilder.indexFields[j], currentData.getKey().getValue(field));
                        }
                    }
                    indexGlobs.add(index);
                });
                saveData(path.resolve(getIndexNameFile(indexName)), indexTypeBuilder.offHeapIndexTypeInfo, indexGlobs, 1000, allStrings);
            }
        }

        private void scan(Node1Elements node, Consumer<Node1Elements> consumer) {
            if (node != null) {
                consumer.accept(node);
                scan(node.val1, consumer);
                scan(node.val2, consumer);
            }
        }

        static Map<String, Glob> createStringsFile(Collection<Glob> globs, OffHeapTypeInfo offHeapTypeInfo, Path pathToFile) throws IOException {
            List<StringField> stringFields = new ArrayList<>();
            for (Field field : offHeapTypeInfo.fields) {
                if (field instanceof StringField stringField) {
                    stringFields.add(stringField);
                }
            }

            Map<String, Glob> allStrings = new HashMap<>();
            int index = 0;
            for (Glob glob : globs) {
                for (StringField stringField : stringFields) {
                    if (!allStrings.containsKey(glob.get(stringField))) {
                        allStrings.put(glob.get(stringField), StringRefType.TYPE.instantiate()
                                .set(StringRefType.id, ++index));

                    }
                }
            }
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

        private static IdentityHashMap<Glob, Integer> saveData(Path pathToFile, OffHeapTypeInfo offHeapTypeInfo1,
                                                               Collection<Glob> globs, int bufferSize, Map<String, Glob> allStrings) throws IOException {

            final MemorySegment memorySegment;
            IdentityHashMap<Glob, Integer> offsets = new IdentityHashMap<>();
            try (Arena arena = Arena.ofConfined()) {
                final int groupSize = Math.toIntExact(offHeapTypeInfo1.groupLayout.byteSize());
                memorySegment = arena.allocate(groupSize * bufferSize);

                try (FileChannel open = FileChannel.open(pathToFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    int i = 0;
                    final Field[] fields = offHeapTypeInfo1.fields;
                    for (Glob glob : globs) {
                        if (offHeapTypeInfo1.type != glob.getType()) {
                            log.error("Bad type " + offHeapTypeInfo1.type.getName() + " but got " + glob.getType().getName());
                        }
                        for (int j = 0; j < fields.length; j++) {
                            if (fields[j] instanceof StringField stringField) {
                                final String str = glob.get(stringField);
                                final Glob index = allStrings.get(str);
                                offHeapTypeInfo1.arrayStringLenHandles[j]
                                        .set(memorySegment, 0L, (long) i, index.get(StringRefType.len));
                                offHeapTypeInfo1.arrayStringAddrHandles[j]
                                        .set(memorySegment, 0L, (long) i, index.get(StringRefType.offset));
                            } else {
                                offHeapTypeInfo1.arrayFieldHandles[j].set(memorySegment, 0L, (long) i, glob.getValue(fields[j]));
                            }
                        }
                        offsets.put(glob, i);
                        i++;
                        if (i == bufferSize) {
                            open.write(memorySegment.asByteBuffer());
                            i = 0;
                        }
                    }
                    if (i != 0) {
                        final ByteBuffer byteBuffer = memorySegment.asByteBuffer();
                        byteBuffer.limit(i * groupSize);
                        open.write(byteBuffer);
                    }
                }
            }
            return offsets;
        }

        @Override
        public void close() throws IOException {
        }

        private static class IndexFunctionalKeyComparator implements Comparator<FunctionalKey> {
            private final Map<String, Glob> allStrings;
            private final Comparator<FunctionalKey>[] comparators;

            public IndexFunctionalKeyComparator(Map<String, Glob> allStrings, Field[] sortFields) {
                this.allStrings = allStrings;
                comparators = new Comparator[sortFields.length];
                for (int i = 0; i < sortFields.length; i++) {
                    Field sortField = sortFields[i];
                    if (sortField instanceof StringField stringField) {
                        comparators[i] = (o1, o2) -> {
                            final Glob glob1 = this.allStrings.get(o1.get(stringField));
                            final Glob glob2 = this.allStrings.get(o2.get(stringField));
                            final int i1 = glob1.getNotNull(StringRefType.offset);
                            final int i2 = glob2.getNotNull(StringRefType.offset);
                            return Integer.compare(i1, i2);
                        };
                    } else {
                        comparators[i] = (o1, o2) ->
                                Utils.compare((Comparable) o1.getValue(sortField),
                                        (Comparable) o2.getValue(sortField));
                    }
                }
            }

            public int compare(FunctionalKey o1, FunctionalKey o2) {
                for (Comparator<FunctionalKey> comparator : comparators) {
                    final int compare = comparator.compare(o1, o2);
                    if (compare != 0) {
                        return compare;
                    }
                }
                return 0;
            }
        }
    }

    private static String getIndexNameFile(String indexName) {
        return indexName + ".data";
    }

    private static class DefaultOffHeapReadService implements OffHeapReadService, StringAccessorByAdd {
        private final int count;
        private final OffHeapTypeInfo offHeapTypeInfo;
        private final FileChannel dataChannel;
        private final FileChannel stringChannel;
        private final Arena arena;
        private final MappedByteBuffer stringBytesBuffer;
        private final IntHashMap<String> readStrings = new IntHashMap<>();
        private final Map<String, DefaultReadOffHeapIndex> indexMap;
        private final MemorySegment memorySegment;
        private byte[] cache = new byte[1024];

        public DefaultOffHeapReadService(Path directory, Arena arena, OffHeapTypeInfo offHeapTypeInfo, Map<String, OffHeapIndex> index) throws IOException {
            this.arena = arena;
            this.dataChannel = FileChannel.open(directory.resolve(CONTENT_DATA), StandardOpenOption.READ);
            this.stringChannel = FileChannel.open(directory.resolve(STRINGS_DATA), StandardOpenOption.READ);
            stringBytesBuffer = stringChannel.map(FileChannel.MapMode.READ_ONLY, 0, stringChannel.size());
            this.count = Math.toIntExact(dataChannel.size() / offHeapTypeInfo.groupLayout.byteSize());
            this.offHeapTypeInfo = offHeapTypeInfo;
            indexMap = new HashMap<>();
            for (Map.Entry<String, OffHeapIndex> entry : index.entrySet()) {
                indexMap.put(entry.getKey(), new DefaultReadOffHeapIndex(directory, entry.getValue(), this));
            }
            memorySegment = dataChannel.map(FileChannel.MapMode.READ_ONLY,
                    0,
                    count * offHeapTypeInfo.groupLayout.byteSize(), arena);

        }

        @Override
        public ReadOffHeapIndex getIndex(OffHeapIndex index) {
            return indexMap.get(index.getName());
        }

        @Override
        public void readAll(Consumer<Glob> consumer) throws IOException {
            Field[] fields = offHeapTypeInfo.fields;

            for (int i = 0; i < count; i++) {
                final MutableGlob instantiate = readArrayGlob(fields, memorySegment, (long) i);
                consumer.accept(instantiate);
            }
        }

        private MutableGlob readArrayGlob(Field[] fields, MemorySegment memorySegment, long i) {
            final MutableGlob instantiate = offHeapTypeInfo.type.instantiate();
            for (int j = 0; j < fields.length; j++) {
                Field field = fields[j];
                if (field instanceof StringField stringField) {
                    int addr = (int) offHeapTypeInfo.arrayStringAddrHandles[j].get(memorySegment, (long) 0, i);
                    int len = (int) offHeapTypeInfo.arrayStringLenHandles[j].get(memorySegment, (long) 0, i);
                    instantiate.set(stringField, get(addr, len));
//                } else if (field instanceof GlobField globField) {
//                    instantiate.set(globField,
//                            readGlob(memorySegment.asSlice(i * offHeapTypeInfo.groupLayout.byteSize(),
//                                    offHeapTypeInfo.globFieldTypeInfo[j].groupLayout.byteSize()), offHeapTypeInfo.globFieldTypeInfo[j]));
                } else {
                    instantiate.setValue(field,
                            offHeapTypeInfo.arrayFieldHandles[j].get(memorySegment, (long) 0, i));
                }
            }
            return instantiate;
        }

        @Override
        public Optional<Glob> read(OffHeapRef offHeapRef) {
            final MutableGlob instantiate = readArrayGlob(offHeapTypeInfo.fields, memorySegment,
                    ((DefaultReadOffHeapIndex.DefaultOffHeapRef) offHeapRef).dataIndex);
            return Optional.of(instantiate);
        }

        @Override
        synchronized public String get(int addr, int len) {
            final String s = readStrings.get(addr);
            if (s == null) {
                if (cache.length < len) {
                    cache = new byte[len];
                }
                stringBytesBuffer.position(addr);
                stringBytesBuffer.get(cache, 0, len);
                readStrings.put(addr, new String(cache, 0, len, StandardCharsets.UTF_8));
            }
            return s;
        }

        private static class DefaultReadOffHeapIndex implements ReadOffHeapIndex {
            private final OffHeapIndex offHeapIndex;
            private final StringAccessorByAdd stringAccessor;
            private final FileChannel indexChannel;
            private final MemorySegment memorySegment;
            private IndexTypeBuilder indexTypeBuilder;

            public DefaultReadOffHeapIndex(Path path, OffHeapIndex offHeapIndex, StringAccessorByAdd stringAccessor) throws IOException {
                this.offHeapIndex = offHeapIndex;
                this.stringAccessor = stringAccessor;
                final String indexName = offHeapIndex.getName();
                indexTypeBuilder = new IndexTypeBuilder(indexName, offHeapIndex.getKeyBuilder().getFields());
                this.indexChannel = FileChannel.open(path.resolve(getIndexNameFile(indexName)), StandardOpenOption.READ);
                memorySegment = indexChannel.map(FileChannel.MapMode.READ_ONLY, 0, indexChannel.size(), Arena.ofShared());
            }

            public OffHeapRef find(FunctionalKey functionalKey) {
                final OffHeapTypeInfo offHeapIndexTypeInfo = indexTypeBuilder.offHeapIndexTypeInfo;
                return binSearch(functionalKey, offHeapIndexTypeInfo, 0);
            }

            private DefaultOffHeapRef binSearch(FunctionalKey functionalKey, OffHeapTypeInfo offHeapIndexTypeInfo, int indexOffset) {
                int compare = compare(offHeapIndexTypeInfo, functionalKey, indexOffset);
                if (compare == 0) {
                    return new DefaultOffHeapRef(
                            (Integer) indexTypeBuilder.dataOffsetArrayHandle.get(memorySegment, 0L, indexOffset));
                }
                if (compare < 0) {
                    int index = (Integer) indexTypeBuilder.indexOffset1ArrayHandle.get(memorySegment, 0L, indexOffset);
                    if (index >= 0) {
                        return binSearch(functionalKey, offHeapIndexTypeInfo, index);
                    } else {
                        return null;
                    }
                } else {
                    int index = (Integer) indexTypeBuilder.indexOffset2ArrayHandle.get(memorySegment, 0L, indexOffset);
                    if (index >= 0) {
                        return binSearch(functionalKey, offHeapIndexTypeInfo, index);
                    } else {
                        return null;
                    }
                }
            }

            private int compare(OffHeapTypeInfo offHeapIndexTypeInfo, FunctionalKey functionalKey, int index) {
                Field[] fields = indexTypeBuilder.keyFields;
                for (int i = 0; i < fields.length; i++) {
                    Field field = fields[i];
                    if (field instanceof StringField stringField) {
                        final String data = functionalKey.get(stringField);
                        int addr = (int) offHeapIndexTypeInfo.arrayStringAddrHandles[i].get(memorySegment, (long) 0, index);
                        int len = (int) offHeapIndexTypeInfo.arrayStringLenHandles[i].get(memorySegment, (long) 0, index);
                        final String s = stringAccessor.get(addr, len);
                        final int cmp = Utils.compare(data, s);
                        if (cmp != 0) {
                            return cmp;
                        }
                    } else {
                        final Comparable value = (Comparable) functionalKey.getValue(field);
                        final Object o = offHeapIndexTypeInfo.arrayFieldHandles[i].get(memorySegment, (long) 0, index);
                        int cmp = Utils.compare(value, o);
                        if (cmp != 0) {
                            return cmp;
                        }
                    }
                }
                return 0;
            }

            private static class DefaultOffHeapRef implements OffHeapRef {
                private final int dataIndex;

                public DefaultOffHeapRef(int dataIndex) {
                    this.dataIndex = dataIndex;
                }
            }
        }
    }


    interface StringAccessorByAdd {
        String get(int addr, int len);
    }


    static class StringRefType {
        public static final GlobType TYPE;

        public static final IntegerField id;

        public static final IntegerField offset;

        public static final IntegerField len;

        static {
            final GlobTypeBuilder typeBuilder = GlobTypeBuilderFactory.create("StringRefType");
            TYPE = typeBuilder.unCompleteType();
            id = typeBuilder.declareIntegerField("id");
            offset = typeBuilder.declareIntegerField("offset");
            len = typeBuilder.declareIntegerField("len");
            typeBuilder.complete();
        }
    }

    static class OffHeapTypeInfo {
        private final GlobType type;
        private final GroupLayout groupLayout;
        private final VarHandle[] fieldsToVarHandles;
        private final VarHandle[] stringFieldLenHandles;
        private final VarHandle[] stringFieldAddrHandles;
        private final VarHandle[] arrayFieldHandles;
        private final VarHandle[] arrayStringAddrHandles;
        private final VarHandle[] arrayStringLenHandles;
        //        private final VarHandle[] globFieldHandles;
//        public final OffHeapTypeInfo[] globFieldTypeInfo;
        private final Field[] fields;

        public OffHeapTypeInfo(GlobType type) {
            this.type = type;
            fields = type.getFields();
            fieldsToVarHandles = new VarHandle[fields.length];
            stringFieldLenHandles = new VarHandle[fields.length];
            stringFieldAddrHandles = new VarHandle[fields.length];
            final var groupLayoutAbstractFieldVisitor = new GroupLayoutAbstractFieldVisitor();
            for (Field field : fields) {
                field.safeAccept(groupLayoutAbstractFieldVisitor);
            }
            groupLayout = groupLayoutAbstractFieldVisitor.createGroupLayout();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field instanceof StringField) {
                    stringFieldAddrHandles[i] =
                            groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName() + SUFFIX_ADDR));
                    stringFieldLenHandles[i] =
                            groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName() + SUFFIX_LEN));
                }
//                else if (field instanceof GlobField) {
//                    globFieldHandles[i] = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName()),
//                            MemoryLayout.PathElement.dereferenceElement())
//                }
                else {
                    fieldsToVarHandles[i] = groupLayout.varHandle(MemoryLayout.PathElement.groupElement(field.getName()));
                }
            }
            this.arrayFieldHandles = new VarHandle[type.getFieldCount()];
            this.arrayStringAddrHandles = new VarHandle[type.getFieldCount()];
            this.arrayStringLenHandles = new VarHandle[type.getFieldCount()];
            for (int i = 0; i < fields.length; i++) {
                if (fields[i] instanceof StringField) {
                    arrayStringLenHandles[i] =
                            groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(fields[i].getName() + SUFFIX_LEN));
                    arrayStringAddrHandles[i] =
                            groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(fields[i].getName() + SUFFIX_ADDR));
                } else {
                    arrayFieldHandles[i] =
                            groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement(fields[i].getName()));
                }
            }
        }
    }

    static class IndexTypeBuilder {
        private final Field[] indexFields;
        private final Field[] keyFields;
        private final IntegerField[] strArr;
        private final IntegerField[] strLen;
        private final IntegerField dataOffset1;
        private final IntegerField offsetVal1;
        private final IntegerField offsetVal2;
        private final GlobType indexType;
        private final OffHeapTypeInfo offHeapIndexTypeInfo;
        private final VarHandle dataOffsetArrayHandle;
        private final VarHandle indexOffset1ArrayHandle;
        private final VarHandle indexOffset2ArrayHandle;

        public IndexTypeBuilder(String indexName, Field[] keyFields) {
            this.keyFields = keyFields;
            final GlobTypeBuilder keyTypeBuilder = GlobTypeBuilderFactory.create(indexName);
            indexFields = new Field[keyFields.length];
            strArr = new IntegerField[keyFields.length];
            strLen = new IntegerField[keyFields.length];
            for (int j = 0; j < keyFields.length; j++) {
                Field field = keyFields[j];
                if (field instanceof StringField) {
                    strArr[j] = keyTypeBuilder.declareIntegerField(field.getName() + SUFFIX_ADDR, List.of());
                    strLen[j] = keyTypeBuilder.declareIntegerField(field.getName() + SUFFIX_LEN, List.of());
                } else {
                    indexFields[j] = keyTypeBuilder.declare(field.getName(), field.getDataType(), List.of());
                }
            }
            dataOffset1 = keyTypeBuilder.declareIntegerField("dataOffset1");

            offsetVal1 = keyTypeBuilder.declareIntegerField("indexOffset1");
            offsetVal2 = keyTypeBuilder.declareIntegerField("indexOffset2");
            indexType = keyTypeBuilder.get();
            offHeapIndexTypeInfo = new OffHeapTypeInfo(indexType);
            dataOffsetArrayHandle = offHeapIndexTypeInfo.groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("dataOffset1"));
            indexOffset1ArrayHandle = offHeapIndexTypeInfo.groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("indexOffset1"));
            indexOffset2ArrayHandle = offHeapIndexTypeInfo.groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("indexOffset2"));

        }
    }

    private static class DefaultOffHeapIndex implements OffHeapIndex {
        private final String name;
        private final FunctionalKeyBuilder functionalKeyBuilder;

        public DefaultOffHeapIndex(String name, FunctionalKeyBuilder functionalKeyBuilder) {
            this.name = name;
            this.functionalKeyBuilder = functionalKeyBuilder;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public FunctionalKeyBuilder getKeyBuilder() {
            return functionalKeyBuilder;
        }
    }
}
