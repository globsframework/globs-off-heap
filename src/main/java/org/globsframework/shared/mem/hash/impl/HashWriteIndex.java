package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.DataSaver;
import org.globsframework.shared.mem.tree.impl.*;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;

public class HashWriteIndex {
    public static final int OFFSET_FOR_DATA = 8;
    private HashIndex hashIndex;
    private int maxCollisions;
    private int nbCollisions;

    public HashWriteIndex(HashIndex hashIndex) {
        this.hashIndex = hashIndex;
    }

    void save(Path path, IdentityHashMap<Glob, Long> offset) throws IOException {
        maxCollisions = 0;
        nbCollisions = 0;
        OffHeapGlobTypeGroupLayout offHeapGlobTypeGroupLayout = OffHeapGlobTypeGroupLayoutImpl.create(PerData.TYPE);
        final RootOffHeapTypeInfo rootOffHeapTypeInfo = new RootOffHeapTypeInfo(OffHeapTypeInfo.create(PerData.TYPE, offHeapGlobTypeGroupLayout.getPrimaryGroupLayout()), Map.of());
        Glob[] index = new Glob[hashIndex.size()];
        List<Glob> linkAtEnd = new ArrayList<>();
        for (Map.Entry<Glob, Long> globLongEntry : offset.entrySet()) {
            final FunctionalKey functionalKey = hashIndex.keyBuilder().create(globLongEntry.getKey());
            int h = hash(functionalKey);
            final int i = tableIndex(h, hashIndex.size());
            final Glob glob = PerData.createValid(h, 0, globLongEntry.getValue());
            if (index[i] == null) {
                index[i] = glob;
            }
            else {
                int collision = 1;
                Glob in = index[i];
                int n;
                while ((n = in.get(PerData.nextIndex)) != 0) {
                    in = linkAtEnd.get(n - hashIndex.size());
                    collision++;
                }
                ((MutableGlob) in).set(PerData.nextIndex, hashIndex.size() + linkAtEnd.size());
                linkAtEnd.add(glob);
                if (collision > maxCollisions) {
                    maxCollisions = collision;
                }
                nbCollisions += collision;
            }
        }
        List<Glob> dataToSave = new ArrayList<>();
        for (int i = 0; i < index.length; i++) {
            dataToSave.add(index[i]);
        }
        for (Glob glob : linkAtEnd) {
            dataToSave.add(glob);
        }
        Glob empty = PerData.TYPE.instantiate()
                .set(PerData.isValid, 2);
        for (int i = 0; i <100; i++) {
            dataToSave.add(empty);
        }

        DataSaver.saveData(path.resolve(DefaultOffHeapTreeService.createContentFileName(PerData.TYPE, hashIndex.name())),
                rootOffHeapTypeInfo,
                dataToSave, 1024 * 1024, Map.of(), Map.of(),
                new DataSaver.UpdateHeader() {
                    @Override
                    public void update(MemorySegment memorySegment, long currenOffset, Glob glob) {
                    }

                    @Override
                    public ByteBuffer getHeaderFile(long nextFree) {
                        final ByteBuffer wrap = ByteBuffer.wrap(new byte[HashWriteIndex.OFFSET_FOR_DATA]);
                        wrap.putLong(hashIndex.size());
                        wrap.flip();
                        return wrap;
                    }
                }, new DataSaver.FreeSpace() {
                    @Override
                    public int freeTypeCountAtEnd(GlobType globType) {
                        return 0;
                    }

                    @Override
                    public int freeByteSpaceAtStart(GlobType globType) {
                        return HashWriteIndex.OFFSET_FOR_DATA;
                    }
                });
//        DataSaver dataSaver = new DataSaver(path, PerData.TYPE, globType -> rootOffHeapTypeInfo);
//        dataSaver.saveData(dataToSave);
    }

    public static int tableIndex(int h, int tableSize) {
        return (tableSize - 1) & h;
    }

    // from java HashMap
    public static int hash(FunctionalKey key) {
        int h;
        return (h = key.hashCode()) ^ (h >>> 16);
    }

    static final int MAXIMUM_CAPACITY = 1 << 30;

    static final int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }



    static public class PerData {
        public static GlobType TYPE;

        public static final IntegerField hash;

        public static final IntegerField nextIndex;

        public static final LongField dataIndex;

        public static final IntegerField isValid; // 1 : valide : 0 invalide, 2 : free

        static {
            final GlobTypeBuilder builder = GlobTypeBuilderFactory.create("HashHeader");
            hash = builder.declareIntegerField("hash");
            nextIndex = builder.declareIntegerField("nextIndex");
            dataIndex = builder.declareLongField("dataIndex");
            isValid = builder.declareIntegerField("isValid");
            TYPE = builder.build();
        }

        public static Glob createValid(int hash, int nextIndex, long dataIndex) {
            return TYPE.instantiate()
                    .set(PerData.hash, hash)
                    .set(PerData.nextIndex, nextIndex)
                    .set(PerData.dataIndex, dataIndex)
                    .set(PerData.isValid, 1);
        }
    }
}
