package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.shared.mem.impl.field.AnyFieldHandleAccess;
import org.globsframework.shared.mem.impl.field.HandleAccess;
import org.globsframework.shared.mem.impl.field.StringFieldHandleAccess;

import java.lang.foreign.GroupLayout;

public class OffHeapTypeInfo {
    public final GlobType type;
    public final GroupLayout groupLayout;
    public final HandleAccess[] handleAccesses;
    public final Field[] fields;
    private final long sizeWithPadding;

    public OffHeapTypeInfo(GlobType type) {
        this.type = type;
        fields = type.getFields();
        final var groupLayoutAbstractFieldVisitor = new GroupLayoutAbstractFieldVisitor();
        for (Field field : fields) {
            field.safeAccept(groupLayoutAbstractFieldVisitor);
        }
        groupLayout = groupLayoutAbstractFieldVisitor.createGroupLayout();
        handleAccesses = new HandleAccess[fields.length];
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (field instanceof StringField) {
                handleAccesses[i] = StringFieldHandleAccess.create(groupLayout, (StringField) field);
            } else {
                handleAccesses[i] = AnyFieldHandleAccess.create(groupLayout, field);
            }
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
