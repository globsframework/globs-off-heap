package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.annotations.ArraySize;
import org.globsframework.core.metamodel.fields.*;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

class GroupLayoutFieldVisitor extends FieldVisitor.AbstractWithErrorVisitor {
    List<MemoryLayout> fieldsLayout = new ArrayList<>();
    int minAlignment = 0;
    int currentPos = 0;

    public void visitString(StringField field) {
        addPadding(4);
        fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.STRING_SUFFIX_ADDR));
        fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.STRING_SUFFIX_LEN));
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

    public void visitBoolean(BooleanField field) {
        addPadding(1);
        fieldsLayout.add(ValueLayout.JAVA_BOOLEAN.withName(field.getName()));
        currentPos += 1;
    }

    @Override
    public void visitDate(DateField field) throws Exception {
        addPadding(8);
        fieldsLayout.add(ValueLayout.JAVA_LONG.withName(field.getName()));
        currentPos += 8;
    }

    @Override
    public void visitDateTime(DateTimeField field) throws Exception {
        addPadding(4);
        fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.DATE_TIME_DATE_SUFFIX));
        fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.DATE_TIME_TIME_SUFFIX));
        fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.DATE_TIME_NANO_SUFFIX));
        fieldsLayout.add(MemoryLayout.sequenceLayout(
                        DefaultOffHeapService.DATE_TIME_MAX_ZONE_ID_SIZE, ValueLayout.JAVA_BYTE)
                .withName(field.getName() + DefaultOffHeapService.DATE_TIME_ZONE_ID_SUFFIX));
        currentPos += 4 + 4 + 4 + DefaultOffHeapService.DATE_TIME_MAX_ZONE_ID_SIZE;
     }

    public void visitIntegerArray(IntegerArrayField field) throws Exception {
        addPadding(4);
        int size = field.getAnnotation(ArraySize.KEY).getNotNull(ArraySize.VALUE);
        fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.STRING_SUFFIX_LEN));
        fieldsLayout.add(
                MemoryLayout.sequenceLayout(size, ValueLayout.JAVA_INT)
                        .withName(field.getName()));
        currentPos += 4 + size * 4;
    }

    public GroupLayout createGroupLayout() {
        addPadding(minAlignment);
        return MemoryLayout.structLayout(fieldsLayout.toArray(new MemoryLayout[0]));
    }
}
