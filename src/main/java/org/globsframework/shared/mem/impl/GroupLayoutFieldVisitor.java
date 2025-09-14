package org.globsframework.shared.mem.impl;

import org.globsframework.core.metamodel.GlobType;
import org.globsframework.core.metamodel.annotations.ArraySize;
import org.globsframework.core.metamodel.annotations.MaxSize;
import org.globsframework.core.metamodel.fields.*;
import org.globsframework.core.model.Glob;
import org.globsframework.shared.mem.model.HeapInline;
import org.globsframework.shared.mem.model.HeapMaxElement;

import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

class GroupLayoutFieldVisitor extends FieldVisitor.AbstractWithErrorVisitor {
    private final GroupLayoutAccessor layoutAccessor;
    List<MemoryLayout> fieldsLayout = new ArrayList<>();
    int minAlignment = 0;
    int currentPos = 0;

    public GroupLayoutFieldVisitor(GroupLayoutAccessor layoutAccessor) {
        this.layoutAccessor = layoutAccessor;
    }

    interface GroupLayoutAccessor {
        MemoryLayout getLayout(GlobType type);
    }

    public void visitString(StringField field) {
        if (field.hasAnnotation(MaxSize.KEY)) {
            addPadding(4);
            fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.STRING_SUFFIX_LEN));
            final int maxSize = field.getAnnotation(MaxSize.KEY).getNotNull(MaxSize.VALUE);
            fieldsLayout.add(MemoryLayout.sequenceLayout(maxSize, ValueLayout.JAVA_CHAR)
                    .withName(field.getName()));
            currentPos += maxSize * 2 + 4;
        }
        else {
            addPadding(4);
            fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.STRING_SUFFIX_LEN));
            fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.STRING_SUFFIX_ADDR));
            currentPos += 8;
        }
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


    public void visitGlob(GlobField field) throws Exception {
        final Glob annotation = field.findAnnotation(HeapInline.UNIQUE_KEY);
        if (annotation != null) {
            fieldsLayout.add(ValueLayout.JAVA_BYTE.withName(field.getName() + DefaultOffHeapService.GLOB_SET));// 1 null, 2 unset
            currentPos += 1;
            addPadding(8);  // layout.byteAlignment();
            final MemoryLayout layout = layoutAccessor.getLayout(field.getTargetType());
            fieldsLayout.add(layout.withName(field.getName()));
            currentPos += (int) layout.byteSize();
        }
        else {
            addPadding(8);
            fieldsLayout.add(ValueLayout.JAVA_LONG.withName(field.getName())); // offset
            currentPos += 8;
        }
    }

    @Override
    public void visitGlobArray(GlobArrayField field) throws Exception {
        final Integer size = field.getAnnotation(HeapMaxElement.UNIQUE_KEY)
                .getNotNull(HeapMaxElement.maxSize);
        final Glob annotation = field.findAnnotation(HeapInline.UNIQUE_KEY);
        fieldsLayout.add(ValueLayout.JAVA_INT.withName(field.getName() + DefaultOffHeapService.GLOB_LEN));
        currentPos += 4;
        if (annotation != null) {
            final MemoryLayout layout = layoutAccessor.getLayout(field.getTargetType());
            addPadding(8); // layout.byteAlignment();
            fieldsLayout.add(MemoryLayout.sequenceLayout(size, layout).withName(field.getName()));
            currentPos += size * (int) layout.byteSize(); // check : il faut que byteSize soit aligné
        }
        else {
            addPadding(8);
            fieldsLayout.add(MemoryLayout.sequenceLayout(size, ValueLayout.JAVA_LONG).withName(field.getName()));
            currentPos += 8 * size;
        }
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
