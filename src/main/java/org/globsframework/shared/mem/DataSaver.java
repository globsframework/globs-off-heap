package org.globsframework.shared.mem;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.field.handleacces.HandleAccess;
import org.globsframework.shared.mem.model.HeapInline;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.RootOffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.write.Flush;
import org.globsframework.shared.mem.tree.impl.write.NextFreeOffset;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;
import org.globsframework.shared.mem.tree.impl.write.StringRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Stream;

public class DataSaver {
    private static final Logger logger = LoggerFactory.getLogger(DataSaver.class);
    private final Path path;
    private final GlobType dataType;
    private final OffHeapTypeInfoAccessor offHeapTypeInfoMap;
    private final UpdateHeaderAccessor updateHeader;
    private final FreeSpace freeSpace;
    private final AdditionalSpaceAtEndOfStringFile additionalSpaceAtEndOfStringFile;

    public DataSaver(Path path, GlobType dataType, OffHeapTypeInfoAccessor offHeapTypeInfoMap) {
        this(path, dataType, offHeapTypeInfoMap, UpdateHeaderAccessor.NO, FreeSpace.NONE, currentSize -> 0);
    }

    public DataSaver(Path path, GlobType dataType, OffHeapTypeInfoAccessor offHeapTypeInfoMap, UpdateHeaderAccessor updateHeader,
                     FreeSpace freeSpace, AdditionalSpaceAtEndOfStringFile additionalSpaceAtEndOfStringFile) {
        this.path = path;
        this.dataType = dataType;
        this.offHeapTypeInfoMap = offHeapTypeInfoMap;
        this.updateHeader = updateHeader;
        this.freeSpace = freeSpace;
        this.additionalSpaceAtEndOfStringFile = additionalSpaceAtEndOfStringFile;
    }

    public static void extractTypeWithVarSize(GlobType type, Set<GlobType> globTypes, Set<GlobType> visited) {
        if (visited.contains(type)) {
            return;
        }
        visited.add(type);
        for (Field field : type.getFields()) {
            switch (field) {
                case GlobArrayUnionField f -> {
                    final Collection<GlobType> targetTypes = f.getTargetTypes();
                    if (!f.hasAnnotation(HeapInline.UNIQUE_KEY)) {
                        globTypes.addAll(targetTypes);
                    }
                    for (GlobType targetType : targetTypes) {
                        extractTypeWithVarSize(targetType, globTypes, visited);
                    }
                }
                case GlobArrayField f -> {
                    if (!f.hasAnnotation(HeapInline.UNIQUE_KEY)) {
                        globTypes.add(f.getTargetType());
                    }
                    extractTypeWithVarSize(f.getTargetType(), globTypes, visited);
                }
                case GlobField f -> {
                    if (!f.hasAnnotation(HeapInline.UNIQUE_KEY)) {
                        globTypes.add(f.getTargetType());
                    }
                    extractTypeWithVarSize(f.getTargetType(), globTypes, visited);
                }
                case GlobUnionField f -> {
                    final Collection<GlobType> targetTypes = f.getTargetTypes();
                    if (!f.hasAnnotation(HeapInline.UNIQUE_KEY)) {
                        globTypes.addAll(targetTypes);
                    }
                    for (GlobType targetType : targetTypes) {
                        extractTypeWithVarSize(targetType, globTypes, visited);
                    }
                }
                default -> {
                }
            }
        }
    }

    public Result saveData(Collection<Glob> globs) throws IOException {
        final Map<GlobType, IdentityHashMap<Glob, Glob>> extracted = extractGlobToSave(globs);
        final Map<String, StringRef> allStrings = getAndSaveVarStrings(globs);
        final Map<GlobType, IdentityHashMap<Glob, Long>> offsets = new HashMap<>();
        for (Map.Entry<GlobType, IdentityHashMap<Glob, Glob>> globTypeIdentityHashMapEntry : extracted.entrySet()) {
            computeOffset(globTypeIdentityHashMapEntry.getKey(), globTypeIdentityHashMapEntry.getValue().keySet(), offsets);
        }
        computeOffset(dataType, globs, offsets);
        if (extracted.isEmpty()) {
            try (FileChannel _ = FileChannel.open(path.resolve(DefaultOffHeapTreeService.createContentFileName(dataType)),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            }
        }

        {
            final RootOffHeapTypeInfo offHeapTypeInfo = offHeapTypeInfoMap.get(dataType);
            saveData(path.resolve(DefaultOffHeapTreeService.createContentFileName(dataType)),
                    offHeapTypeInfo,
                    globs, 1024 * 1024, allStrings, offsets,
                    updateHeader.getUpdateHeader(offHeapTypeInfo.primary().type, offHeapTypeInfo.primary().groupLayout), freeSpace);
        }

        for (Map.Entry<GlobType, IdentityHashMap<Glob, Glob>> globTypeIdentityHashMapEntry : extracted.entrySet()) {
            final RootOffHeapTypeInfo rootOffHeapTypeInfo = offHeapTypeInfoMap.get(globTypeIdentityHashMapEntry.getKey());
            final OffHeapTypeInfo offHeapTypeInfo = rootOffHeapTypeInfo.primary();
            saveData(path.resolve(DefaultOffHeapTreeService.createContentFileName(globTypeIdentityHashMapEntry.getKey())),
                    rootOffHeapTypeInfo,
                    globTypeIdentityHashMapEntry.getValue().keySet(), 1024 * 1024, allStrings, offsets,
                    updateHeader.getUpdateHeader(offHeapTypeInfo.type, offHeapTypeInfo.groupLayout), freeSpace);
        }
        return new Result(allStrings, offsets);
    }

    private void computeOffset(GlobType key, Collection<Glob> globs, Map<GlobType, IdentityHashMap<Glob, Long>> offsets) {
        int position = 0;
        final RootOffHeapTypeInfo offHeapTypeInfo = offHeapTypeInfoMap.get(key);
        if (offHeapTypeInfo == null) {
            throw new RuntimeException("Bug : no OffHeapTypeInfo found for type " + key);
        }
        for (Glob glob : globs) {
            if (glob != null) {
                offsets.computeIfAbsent(glob.getType(), type -> new IdentityHashMap<>())
                        .put(glob, position * offHeapTypeInfo.primary().byteSizeWithPadding());
            }
            position++;
        }
    }

    public record Result(Map<String, StringRef> allStrings, Map<GlobType, IdentityHashMap<Glob, Long>> offsets) {
    }

    public static Map<GlobType, IdentityHashMap<Glob, Glob>> extractGlobToSave(Collection<Glob> l1) {
        Map<GlobType, IdentityHashMap<Glob, Glob>> result = new HashMap<>();
        for (Glob glob : l1) {
            if (glob != null) {
                extractGlobToSave(glob, result);
            }
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

    private Map<String, StringRef> getAndSaveVarStrings(Collection<Glob> root) throws IOException {
        final Map<String, StringRef> stringGlobMap = new HashMap<>();
        getStringGlobMap(root, stringGlobMap);
        if (stringGlobMap.isEmpty()) {
            return Map.of();
        }
        return createStringsFile(path.resolve(DefaultOffHeapTreeService.STRINGS_DATA), stringGlobMap);
    }

    public interface AdditionalSpaceAtEndOfStringFile {
        int size(int currentSize);
    }

    Map<String, StringRef> createStringsFile(Path pathToFile, Map<String, StringRef> allStrings) throws IOException {
        try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(pathToFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            int offset = 0;
            byte[] forLen = new byte[4];
            final ByteBuffer wrap = ByteBuffer.wrap(forLen);
            for (Map.Entry<String, StringRef> entry : allStrings.entrySet()) {
                final byte[] bytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                final StringRef value = (StringRef) entry.getValue();
                value.len =  bytes.length;
                value.offset = offset + 4;
                wrap.position(0);
                wrap.putInt(bytes.length);
                stream.write(forLen);
                stream.write(bytes);
                offset += bytes.length + 4;
            }
            final int sizeIncrement = additionalSpaceAtEndOfStringFile.size(offset);
            for (int i = 0; i < sizeIncrement; i++) {
                stream.write(0);
            }
        }
        return allStrings;
    }

    public static void saveData(Path pathToFile, RootOffHeapTypeInfo offHeapTypeInfo,
                                Collection<Glob> globs, int bufferSize, Map<String, StringRef> allStrings,
                                Map<GlobType, IdentityHashMap<Glob, Long>> offsets) throws IOException {
        saveData(pathToFile, offHeapTypeInfo, globs, bufferSize, allStrings, offsets, UpdateHeader.NO, FreeSpace.NONE);
    }

    public static void saveData(Path pathToFile, RootOffHeapTypeInfo offHeapTypeInfo,
                                Collection<Glob> globs, int bufferSize, Map<String, StringRef> allStrings,
                                Map<GlobType, IdentityHashMap<Glob, Long>> offsets, UpdateHeader updateHeader,
                                FreeSpace freeSpace) throws IOException {
        final MemorySegment memorySegment;
        final OffHeapTypeInfo heapTypeInfo = offHeapTypeInfo.primary();
        final IdentityHashMap<Glob, Long> offsetToCheck = offsets.get(heapTypeInfo.type);
        try (Arena arena = Arena.ofConfined()) {
            memorySegment = arena.allocate(bufferSize);

            try (FileChannel open = FileChannel.open(pathToFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                final int offsetAtStart = freeSpace.freeByteSpaceAtStart(heapTypeInfo.type);
                if (offsetAtStart != 0) {
                    if (open.write(ByteBuffer.wrap(new byte[offsetAtStart])) != offsetAtStart) {
                        throw new RemoteException("Unable to write " + offsetAtStart + " bytes at start of file");
                    }
                }
                final NextFreeOffset nextFreeOffset = new NextFreeOffset();
                final Flush flush = new Flush() {
                    final ByteBuffer byteBuffer = memorySegment.asByteBuffer();

                    @Override
                    public void flush() throws IOException {
                        if (nextFreeOffset.memorySegmentFreeOffset == 0) {
                            return;
                        }
                        byteBuffer.limit(Math.toIntExact(nextFreeOffset.memorySegmentFreeOffset));
                        if (open.write(byteBuffer) != byteBuffer.limit()) {
                            throw new RemoteException("Unable to write " + byteBuffer.limit() + ".");
                        }
                        byteBuffer.clear();
                        nextFreeOffset.memorySegmentFreeOffset = 0;
                    }
                };
                final SaveContext saveContext = new SaveContext(offsets, offHeapTypeInfo.inline()::get, allStrings::get);

                final long groupSize = heapTypeInfo.byteSizeWithPadding();
                for (Glob glob : globs) {
                    if (glob != null) {
                        if (heapTypeInfo.type != glob.getType()) {
                            final String s = "Bad type " + heapTypeInfo.type.getName() + " but got " + glob.getType().getName();
                            logger.error(s);
                            throw new RemoteException(s);
                        }
                    }
//                    final long offset = nextFreeOffset.globalFreeOffset;
//                    if (glob != null && offsetToCheck != null && offsetToCheck.get(glob) != offset) {
//                        throw new RuntimeException("Offset mismatch " + offsetToCheck.get(glob) + " != " + offset + " for " +
//                                                   heapTypeInfo.type.getName() + " and data : " + glob);
//                    }
                    saveGlob(heapTypeInfo, bufferSize, glob, saveContext, groupSize, memorySegment, updateHeader, nextFreeOffset, flush);
                }
                flush.flush();
                final long nextFree = nextFreeOffset.globalFreeOffset;
                final int i = freeSpace.freeTypeCountAtEnd(heapTypeInfo.type);
                for (int j = 0; j < i; j++) {
                    saveGlob(heapTypeInfo, bufferSize, null, saveContext, groupSize, memorySegment, updateHeader, nextFreeOffset, flush);
                }
                flush.flush();
                final ByteBuffer headerFile = updateHeader.getHeaderFile(nextFree);
                if (headerFile != null) {
                    open.position(0);
                    open.write(headerFile);
                }
            }
        }
    }

    public static void getStringGlobMap(Collection<Glob> globs, Map<String, StringRef> allStrings) {
        Map<GlobType, FieldToScan> stringFieldsMap = new HashMap<>();
        int index = 0;
        for (Glob glob : globs) {
            if (glob != null) {
                index = scanForString(allStrings, stringFieldsMap, glob, index);
            }
        }
    }

    private static int scanForString(Map<String, StringRef> allStrings, Map<GlobType, FieldToScan> stringFieldsMap, Glob glob, int index) {
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
                allStrings.put(glob.get(stringField), new StringRef());
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

    private static void saveGlob(OffHeapTypeInfo offHeapTypeInfo1, int bufferSize, Glob glob, SaveContext saveContext,
                                 long groupSize, MemorySegment memorySegment, UpdateHeader updateHeader, NextFreeOffset nextFreeOffset, Flush flush) throws IOException {
        if (groupSize >= bufferSize) {
            throw new RuntimeException("not enough bytes for group layout " + groupSize + " vs available " + bufferSize);
        }
        nextFreeOffset.globalFreeOffset += groupSize;
        if (nextFreeOffset.memorySegmentFreeOffset + groupSize >= bufferSize) {
            flush.flush();
        }
        final long currenOffset = nextFreeOffset.memorySegmentFreeOffset;
        updateHeader.update(memorySegment, currenOffset, glob);
        if (glob != null) {
            for (HandleAccess handleAccess : offHeapTypeInfo1.handleAccesses) {
                handleAccess.save(glob, memorySegment, currenOffset, saveContext);
            }
        }
        nextFreeOffset.memorySegmentFreeOffset += groupSize;
        //callback other save.
    }

    record FieldToScan(StringField[] stringFields, GlobField[] globFields, GlobArrayField[] globArrayFields,
                       GlobUnionField[] globUnionFields, GlobArrayUnionField[] globArrayUnionFields) {
    }

    public interface UpdateHeaderAccessor {
        UpdateHeaderAccessor NO = (globType, groupLayout) -> UpdateHeader.NO;
        UpdateHeader getUpdateHeader(GlobType type, GroupLayout groupLayout);
    }

    public interface UpdateHeader {
        UpdateHeader NO = new UpdateHeader() {
            @Override
            public void update(MemorySegment memorySegment, long currenOffset, Glob glob) {
            }

            @Override
            public ByteBuffer getHeaderFile(long nextFree) {
                return null;
            }
        };
        void update(MemorySegment memorySegment, long currenOffset, Glob glob);

        ByteBuffer getHeaderFile(long nextFree);
    }

    public interface FreeSpace {
        FreeSpace NONE = new FreeSpace() {
            public int freeTypeCountAtEnd(GlobType globType) {
                return 0;
            }
            public int freeByteSpaceAtStart(GlobType globType) {
                return 0;
            }
        };
        int freeTypeCountAtEnd(GlobType globType);

        int freeByteSpaceAtStart(GlobType globType);
    }
}
