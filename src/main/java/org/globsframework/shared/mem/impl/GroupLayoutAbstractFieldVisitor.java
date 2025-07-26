package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.fields.*;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

class GroupLayoutAbstractFieldVisitor extends FieldVisitor.AbstractFieldVisitor {
    List<MemoryLayout> fieldsLayout = new ArrayList<>();
    int minAlignment = 0;
    int currentPos = 0;

    public void visitString(StringField field) {
        addPadding(4);
        fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.SUFFIX_ADDR));
        fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.SUFFIX_LEN));
        currentPos += 8;
    }

    private void addPadding(int size) {
        if (currentPos % size != 0) {
            final int byteSize = size - (currentPos % size);
            fieldsLayout.add(MemoryLayout.paddingLayout(byteSize));
            currentPos += byteSize;
        }
        minAlignment = Math.max(minAlignment, size);
    }

    public void visitInteger(IntegerField field) {
        addPadding(4);
        fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName()));
        currentPos += 4;
    }

    public void visitLong(LongField field) {
        addPadding(8);
        fieldsLayout.add(ValueLayout.JAVA_LONG.withName(field.getName()));
        currentPos += 8;
    }

    public void visitDouble(DoubleField field) {
        addPadding(8);
        fieldsLayout.add(ValueLayout.JAVA_DOUBLE.withName(field.getName()));
        currentPos += 8;
    }

    public GroupLayout createGroupLayout() {
        addPadding(minAlignment);
        return MemoryLayout.structLayout(fieldsLayout.toArray(new MemoryLayout[0]));
    }
}
