package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.IntegerField;
import org.globsframework.core.metamodel.fields.LongField;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.globaccessor.set.GlobSetIntAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetLongAccessor;
import org.globsframework.shared.mem.impl.field.AnyDataAccess;
import org.globsframework.shared.mem.impl.field.DataAccess;
import org.globsframework.shared.mem.impl.field.StringDataAccess;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.VarHandle;
import java.util.List;

public class IndexTypeBuilder {
    public final Field[] indexFields;
    public final Field[] keyFields;
    public final DataAccess[] dataAccesses;
    public final IntegerField[] strArr;
    public final IntegerField[] strLen;
    public final LongField dataOffset1;     // WARN if dataLenOffset1 == 1 dataOffset1 is directly the offset of the data
    public final GlobSetLongAccessor dataOffset1Accessor;
    public final IntegerField dataLenOffset1;  // => ELSE dataOffset1 is the offset in the array off data (index not unique)
    public final GlobSetIntAccessor dataLenOffset1Accessor;
    public final IntegerField offsetVal1;
    public final GlobSetIntAccessor offsetVal1Accessor;
    public final IntegerField offsetVal2;
    public final GlobSetIntAccessor offsetVal2Accessor;
    public final GlobType indexType;
    public final OffHeapTypeInfo offHeapIndexTypeInfo;
    public final VarHandle dataOffsetArrayHandle;
    public final VarHandle dataLenOffsetArrayHandle;
    public final VarHandle indexOffset1ArrayHandle;
    public final VarHandle indexOffset2ArrayHandle;

    public IndexTypeBuilder(String indexName, Field[] keyFields) {
        this.keyFields = keyFields;
        dataAccesses = new DataAccess[keyFields.length];
        final GlobTypeBuilder keyTypeBuilder = GlobTypeBuilderFactory.create(indexName);
        indexFields = new Field[keyFields.length];
        strArr = new IntegerField[keyFields.length];
        strLen = new IntegerField[keyFields.length];
        for (int j = 0; j < keyFields.length; j++) {
            Field field = keyFields[j];
            if (field instanceof StringField) {
                strArr[j] = keyTypeBuilder.declareIntegerField(field.getName() + DefaultOffHeapService.SUFFIX_ADDR, List.of());
                strLen[j] = keyTypeBuilder.declareIntegerField(field.getName() + DefaultOffHeapService.SUFFIX_LEN, List.of());
            } else {
                indexFields[j] = keyTypeBuilder.declare(field.getName(), field.getDataType(), List.of());
            }
        }
        dataOffset1 = keyTypeBuilder.declareLongField("dataOffset1");
        dataLenOffset1 = keyTypeBuilder.declareIntegerField("dataLenOffset1");

        offsetVal1 = keyTypeBuilder.declareIntegerField("indexOffset1");
        offsetVal2 = keyTypeBuilder.declareIntegerField("indexOffset2");
        indexType = keyTypeBuilder.get();
        dataOffset1Accessor = indexType.getGlobFactory().getSetAccessor(dataOffset1);
        dataLenOffset1Accessor = indexType.getGlobFactory().getSetAccessor(dataLenOffset1);
        offsetVal1Accessor = indexType.getGlobFactory().getSetAccessor(offsetVal1);
        offsetVal2Accessor = indexType.getGlobFactory().getSetAccessor(offsetVal2);
        offHeapIndexTypeInfo = new OffHeapTypeInfo(indexType);
        final GroupLayout groupLayout = offHeapIndexTypeInfo.groupLayout;

        for (int i = 0; i < keyFields.length; i++) {
            Field keyField = keyFields[i];
            if (keyField instanceof StringField stringField) {
                dataAccesses[i] = StringDataAccess.create(groupLayout, stringField);
            } else {
                dataAccesses[i] = AnyDataAccess.create(groupLayout, keyField);
            }
        }

        dataOffsetArrayHandle = groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("dataOffset1"));
        dataLenOffsetArrayHandle = groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("dataLenOffset1"));
        indexOffset1ArrayHandle = groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("indexOffset1"));
        indexOffset2ArrayHandle = groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("indexOffset2"));
    }
}
