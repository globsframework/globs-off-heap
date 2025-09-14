package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.GlobTypeBuilder;
import org.globsframework.core.metamodel.GlobTypeBuilderFactory;
import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.globaccessor.set.GlobSetIntAccessor;
import org.globsframework.core.model.globaccessor.set.GlobSetLongAccessor;
import org.globsframework.shared.mem.impl.field.dataaccess.*;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.VarHandle;

public class IndexTypeBuilder {
    public final Field[] indexFields;
    public final Field[] keyFields;
    public final DataAccess[] dataAccesses;
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
        for (int j = 0; j < keyFields.length; j++) {
            Field field = keyFields[j];
            indexFields[j] = keyTypeBuilder.declare(field.getName(), field.getDataType(), field.streamAnnotations().toList());
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
        offHeapIndexTypeInfo = OffHeapTypeInfo.create(indexType, OffHeapGlobTypeGroupLayoutImpl.create(indexType));
        final GroupLayout groupLayout = offHeapIndexTypeInfo.groupLayout;

        for (int i = 0; i < keyFields.length; i++) {
            Field keyField = keyFields[i];
            dataAccesses[i] = switch (keyField) {
                case StringField field -> field.hasAnnotation(MaxSize.KEY) ?
                        FixSizeStringDataAccess.create(groupLayout, field) : StringDataAccess.create(groupLayout, field);
                case IntegerField field -> IntDataAccess.create(groupLayout, field);
                case LongField field -> LongDataAccess.create(groupLayout, field);
                case DoubleField field -> DoubleDataAccess.create(groupLayout, field);
                case BooleanField field -> BooleanDataAccess.create(groupLayout, field);
                case DateField field -> DateDataAccess.create(groupLayout, field);
                default -> AnyDataAccess.create(groupLayout, keyField);
            };
        }

        dataOffsetArrayHandle = groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("dataOffset1"));
        dataLenOffsetArrayHandle = groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("dataLenOffset1"));
        indexOffset1ArrayHandle = groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("indexOffset1"));
        indexOffset2ArrayHandle = groupLayout.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("indexOffset2"));
    }
}
