package org.globsframework.shared.mem;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.field.handleacces.HandleAccess;
import org.globsframework.shared.mem.model.HeapInline;
import org.globsframework.shared.mem.tree.impl.DefaultOffHeapTreeService;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;
import org.globsframework.shared.mem.tree.impl.write.Flush;
import org.globsframework.shared.mem.tree.impl.write.FreeOffset;
import org.globsframework.shared.mem.tree.impl.write.SaveContext;
import org.globsframework.shared.mem.tree.impl.write.StringRefType;
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
    private final Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap;
    private final UpdateHeaderAccessor updateHeader;
    private final FreeSpace freeSpace;

    public DataSaver(Path path, GlobType dataType, Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap) {
        this(path, dataType, offHeapTypeInfoMap, UpdateHeaderAccessor.NO, FreeSpace.NONE);
    }

    public DataSaver(Path path, GlobType dataType, Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap, UpdateHeaderAccessor updateHeader, FreeSpace freeSpace) {
        this.path = path;
        this.dataType = dataType;
        this.offHeapTypeInfoMap = offHeapTypeInfoMap;
        this.updateHeader = updateHeader;
        this.freeSpace = freeSpace;
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
        final Map<String, Glob> allStrings = getAndSaveVarStrings(globs);
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
            final OffHeapTypeInfo offHeapTypeInfo = offHeapTypeInfoMap.get(dataType);
            saveData(path.resolve(DefaultOffHeapTreeService.createContentFileName(dataType)),
                    offHeapTypeInfo, offHeapTypeInfoMap,
                    globs, 1024 * 1024, allStrings, offsets,
                    updateHeader.getUpdateHeader(offHeapTypeInfo.type, offHeapTypeInfo.groupLayout), freeSpace);
        }

        for (Map.Entry<GlobType, IdentityHashMap<Glob, Glob>> globTypeIdentityHashMapEntry : extracted.entrySet()) {
            final OffHeapTypeInfo offHeapTypeInfo = offHeapTypeInfoMap.get(globTypeIdentityHashMapEntry.getKey());
            saveData(path.resolve(DefaultOffHeapTreeService.createContentFileName(globTypeIdentityHashMapEntry.getKey())),
                    offHeapTypeInfo, offHeapTypeInfoMap,
                    globTypeIdentityHashMapEntry.getValue().keySet(), 1024 * 1024, allStrings, offsets,
                    updateHeader.getUpdateHeader(offHeapTypeInfo.type, offHeapTypeInfo.groupLayout), freeSpace);
        }
        return new Result(allStrings, offsets);
    }

    private void computeOffset(GlobType key, Collection<Glob> globs, Map<GlobType, IdentityHashMap<Glob, Long>> offsets) {
        int position = 0;
        final OffHeapTypeInfo offHeapTypeInfo = offHeapTypeInfoMap.get(key);
        if (offHeapTypeInfo == null) {
            throw new RuntimeException("Bug : no OffHeapTypeInfo found for type " + key);
        }
        for (Glob glob : globs) {
            if (glob != null) {
                offsets.computeIfAbsent(glob.getType(), type -> new IdentityHashMap<>())
                        .put(glob, position * offHeapTypeInfo.byteSizeWithPadding());
            }
            position++;
        }
    }

    public record Result(Map<String, Glob> allStrings, Map<GlobType, IdentityHashMap<Glob, Long>> offsets) {
    }

    private static Map<GlobType, IdentityHashMap<Glob, Glob>> extractGlobToSave(Collection<Glob> l1) {
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

    private Map<String, Glob> getAndSaveVarStrings(Collection<Glob> root) throws IOException {
        final Map<String, Glob> stringGlobMap = new HashMap<>();
        getStringGlobMap(root, stringGlobMap);
        if (stringGlobMap.isEmpty()) {
            return Map.of();
        }
        return createStringsFile(path.resolve(DefaultOffHeapTreeService.STRINGS_DATA), stringGlobMap);
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

    public static void saveData(Path pathToFile, OffHeapTypeInfo offHeapTypeInfo,
                                Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap, Collection<Glob> globs, int bufferSize, Map<String, Glob> allStrings,
                                Map<GlobType, IdentityHashMap<Glob, Long>> offsets) throws IOException {
        saveData(pathToFile, offHeapTypeInfo, offHeapTypeInfoMap, globs, bufferSize, allStrings, offsets, UpdateHeader.NO, FreeSpace.NONE);
    }

    public static void saveData(Path pathToFile, OffHeapTypeInfo offHeapTypeInfo,
                                Map<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap, Collection<Glob> globs, int bufferSize, Map<String, Glob> allStrings,
                                Map<GlobType, IdentityHashMap<Glob, Long>> offsets, UpdateHeader updateHeader,
                                FreeSpace freeSpace) throws IOException {
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
                    if (glob != null) {
                        if (offHeapTypeInfo.type != glob.getType()) {
                            final String s = "Bad type " + offHeapTypeInfo.type.getName() + " but got " + glob.getType().getName();
                            logger.error(s);
                            throw new RemoteException(s);
                        }
                    }
                    final long offset = saveContext.freeOffset().globalFreeOffset;
                    if (glob != null && offsetToCheck != null && offsetToCheck.get(glob) != offset) {
                        throw new RuntimeException("Offset mismatch " + offsetToCheck.get(glob) + " != " + offset + " for " +
                                                   offHeapTypeInfo.type.getName() + " and data : " + glob);
                    }
                    saveGlob(offHeapTypeInfo, bufferSize, glob, saveContext, groupSize, memorySegment, updateHeader);
                }
                saveContext.flush().flush();
                final int i = freeSpace.freeSpace(offHeapTypeInfo.type);
                for (int j = 0; j < i; j++) {
                    saveGlob(offHeapTypeInfo, bufferSize, null, saveContext, groupSize, memorySegment, updateHeader);
                }
            }
        }
    }

    private static void getStringGlobMap(Collection<Glob> globs, Map<String, Glob> allStrings) {
        Map<GlobType, FieldToScan> stringFieldsMap = new HashMap<>();
        int index = 0;
        for (Glob glob : globs) {
            if (glob != null) {
                index = scanForString(allStrings, stringFieldsMap, glob, index);
            }
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

    private static void saveGlob(OffHeapTypeInfo offHeapTypeInfo1, int bufferSize, Glob glob, SaveContext saveContext,
                                 long groupSize, MemorySegment memorySegment, UpdateHeader updateHeader) throws IOException {
        if (groupSize >= bufferSize) {
            throw new RuntimeException("not enough bytes for group layout " + groupSize + " vs available " + bufferSize);
        }
        saveContext.freeOffset().globalFreeOffset += groupSize;
        if (saveContext.freeOffset().memorySegmentFreeOffset + groupSize >= bufferSize) {
            saveContext.flush().flush();
        }
        final long currenOffset = saveContext.freeOffset().memorySegmentFreeOffset;
        updateHeader.update(memorySegment, currenOffset, glob);
        if (glob != null) {
            for (HandleAccess handleAccess : offHeapTypeInfo1.handleAccesses) {
                handleAccess.save(glob, memorySegment, currenOffset, saveContext);
            }
        }
        saveContext.freeOffset().memorySegmentFreeOffset += groupSize;
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
        };
        void update(MemorySegment memorySegment, long currenOffset, Glob glob);
    }

    public interface FreeSpace {
        FreeSpace NONE = new FreeSpace() {
            public int freeSpace(GlobType globType) {
                return 0;
            }
        };
        int freeSpace(GlobType globType);
    }
}
