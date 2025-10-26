package org.globsframework.shared.mem.tree.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.shared.mem.field.handleacces.*;
import org.globsframework.shared.mem.model.HeapInline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.GroupLayout;

public class OffHeapTypeInfo {
    private static final Logger log = LoggerFactory.getLogger(OffHeapTypeInfo.class);
    public final GlobType type;
    public final GroupLayout groupLayout;
    public final HandleAccess[] handleAccesses;
    private final long sizeWithPadding;
    public final Field[] fields;

    public OffHeapTypeInfo(GlobType type, GroupLayout groupLayout, HandleAccess[] handleAccesses, long sizeWithPadding) {
        this.type = type;
        fields = type.getFields();
        this.groupLayout = groupLayout;
        this.handleAccesses = handleAccesses;
        this.sizeWithPadding = sizeWithPadding;
    }

    public static OffHeapTypeInfo create(GlobType type, GroupLayout groupLayout) {
        final Field[] fields = type.getFields();
        HandleAccess[] handleAccesses = new HandleAccess[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            handleAccesses[i] = switch (field) {
                case BooleanField booleanField -> BooleanFieldHandleAccess.create(groupLayout, booleanField);
                case IntegerField integerField -> IntegerFieldHandleAccess.create(groupLayout, integerField);
                case DoubleField doubleField -> DoubleFieldHandleAccess.create(groupLayout, doubleField);
                case LongField longField -> LongFieldHandleAccess.create(groupLayout, longField);
                case StringField strField -> strField.hasAnnotation(MaxSize.KEY) ?
                        FixSizeStringFieldHandleAccess.create(groupLayout, strField) :
                        StringFieldHandleAccess.create(groupLayout, strField);
                case DateField dateField -> DateFieldHandleAccess.create(groupLayout, dateField);
                case GlobField globField -> globField.hasAnnotation(HeapInline.UNIQUE_KEY) ?
                        GlobInlineHandleAccess.create(groupLayout, globField) : GlobHandleAccess.create(groupLayout, globField);
                case DateTimeField dateField -> DateTimeFieldHandleAccess.create(groupLayout, dateField);
                case IntegerArrayField integerArrayField ->
                        IntArrayFieldHandleAccess.create(groupLayout, integerArrayField);
                case GlobArrayField globArrayField -> globArrayField.hasAnnotation(HeapInline.UNIQUE_KEY) ?
                        GlobArrayInlineHandleAccess.create(groupLayout, globArrayField) : GlobArrayHandleAccess.create(groupLayout, globArrayField);
                default ->
                        throw new RuntimeException("Field " + field.getDataType() + " not supported for " + field.getFullName());
            };
        }
        final long sizeWithPadding = computeSizeWithPadding(groupLayout);
        return new OffHeapTypeInfo(type, groupLayout, handleAccesses, sizeWithPadding);
    }

    public static long computeSizeWithPadding(GroupLayout groupLayout) {
        long mod = groupLayout.byteSize() % groupLayout.byteAlignment();
        return groupLayout.byteSize() + (mod != 0 ? groupLayout.byteAlignment() - mod : 0);
    }

    public long byteSizeWithPadding() {
        return sizeWithPadding;
    }

    public long byteSize() {
        return groupLayout.byteSize();
    }
}
