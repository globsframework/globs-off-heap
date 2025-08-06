package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.shared.mem.impl.field.handleacces.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.GroupLayout;

public class OffHeapTypeInfo {
    private static final Logger log = LoggerFactory.getLogger(OffHeapTypeInfo.class);
    public final GlobType type;
    public final GroupLayout groupLayout;
    public final HandleAccess[] handleAccesses;
    public final Field[] fields;
    private final long sizeWithPadding;

    public OffHeapTypeInfo(GlobType type) {
        this.type = type;
        fields = type.getFields();
        final var groupLayoutAbstractFieldVisitor = new GroupLayoutFieldVisitor();
        for (Field field : fields) {
            field.safeAccept(groupLayoutAbstractFieldVisitor);
        }
        groupLayout = groupLayoutAbstractFieldVisitor.createGroupLayout();
        handleAccesses = new HandleAccess[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            handleAccesses[i] = switch (field) {
                case BooleanField booleanField -> BooleanFieldHandleAccess.create(groupLayout, booleanField);
                case IntegerField integerField -> IntegerFieldHandleAccess.create(groupLayout, integerField);
                case DoubleField doubleField -> DoubleFieldHandleAccess.create(groupLayout, doubleField);
                case LongField longField -> LongFieldHandleAccess.create(groupLayout, longField);
                case StringField strField -> StringFieldHandleAccess.create(groupLayout, strField);
                case DateField dateField -> DateFieldHandleAccess.create(groupLayout, dateField);
                case DateTimeField dateField -> DateTimeFieldHandleAccess.create(groupLayout, dateField);
                case IntegerArrayField integerArrayField ->
                        IntArrayFieldHandleAccess.create(groupLayout, integerArrayField);
                default ->
                        throw new RuntimeException("Field " + field.getDataType() + " not supported for " + field.getFullName());
            };
        }
        long mod = groupLayout.byteSize() % groupLayout.byteAlignment();
        sizeWithPadding = groupLayout.byteSize() + (mod != 0 ? groupLayout.byteAlignment() - mod : 0);
    }

    public long byteSizeWithPadding() {
        return sizeWithPadding;
    }

    public long byteSize() {
        return groupLayout.byteSize();
    }
}
