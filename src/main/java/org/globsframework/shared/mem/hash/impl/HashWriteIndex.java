package org.globsframework.shared.mem.hash.impl;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.functional.FunctionalKeyBuilder;
import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.BooleanField;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.model.Glob;
import org.globsframework.core.model.MutableGlob;
import org.globsframework.shared.mem.DataSaver;
import org.globsframework.shared.mem.tree.impl.OffHeapGlobTypeGroupLayoutImpl;
import org.globsframework.shared.mem.tree.impl.OffHeapTypeInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class HashWriteIndex {
    private final int tableSize;
    private final IdentityHashMap<Glob, Long> offset;
    private final FunctionalKeyBuilder keyBuilder;
    private int maxCollisions;
    private int nbCollisions;

    public HashWriteIndex(int tableSize, IdentityHashMap<Glob, Long> offset, FunctionalKeyBuilder keyBuilder) {
        this.tableSize = tableSize;
        this.offset = offset;
        this.keyBuilder = keyBuilder;
    }

    void save(Path path) throws IOException {
        maxCollisions = 0;
        nbCollisions = 0;
        final HashMap<GlobType, OffHeapTypeInfo> offHeapTypeInfoMap = new HashMap<>();
        offHeapTypeInfoMap.put(PerData.TYPE, OffHeapTypeInfo.create(PerData.TYPE, OffHeapGlobTypeGroupLayoutImpl.create(PerData.TYPE)));
        Glob[] index = new Glob[tableSize];
        List<Glob> linkAtEnd = new ArrayList<>();
        for (Map.Entry<Glob, Long> globLongEntry : this.offset.entrySet()) {
            final FunctionalKey functionalKey = keyBuilder.create(globLongEntry.getKey());
            int h = hash(functionalKey);
            final int i = tableIndex(h, tableSize);
            final Glob glob = PerData.create(h, 0, globLongEntry.getValue(), true);
            if (index[i] == null) {
                index[i] = glob;
            }
            else {
                int collision = 1;
                Glob in = index[i];
                int n;
                while ((n = in.get(PerData.nextIndex)) != 0) {
                    in = linkAtEnd.get(n - tableSize);
                    collision++;
                }
                ((MutableGlob) in).set(PerData.nextIndex, tableSize + linkAtEnd.size());
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
        DataSaver dataSaver = new DataSaver(path, PerData.TYPE, offHeapTypeInfoMap);
        dataSaver.saveData(dataToSave);
    }

    public static int tableIndex(int h, int tableSize1) {
        return (tableSize1 - 1) & h;
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

        public static final BooleanField isValid;

        static {
            final GlobTypeBuilder builder = GlobTypeBuilderFactory.create("HashHeader");
            TYPE = builder.unCompleteType();
            hash = builder.declareIntegerField("hash");
            nextIndex = builder.declareIntegerField("nextIndex");
            dataIndex = builder.declareLongField("dataIndex");
            isValid = builder.declareBooleanField("isValid");
            builder.complete();
        }

        public static Glob create(int hash, int nextIndex, long dataIndex, boolean isValid) {
            return TYPE.instantiate()
                    .set(PerData.hash, hash)
                    .set(PerData.nextIndex, nextIndex)
                    .set(PerData.dataIndex, dataIndex)
                    .set(PerData.isValid, isValid);
        }
    }
}
