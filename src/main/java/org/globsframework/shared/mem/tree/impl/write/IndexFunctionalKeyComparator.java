package org.globsframework.shared.mem.tree.impl.write;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.metamodel.fields.Field;

import java.util.Comparator;

public class IndexFunctionalKeyComparator implements Comparator<FunctionalKey> {
    private final Comparator<FunctionalKey>[] comparators;

    public IndexFunctionalKeyComparator(Field[] sortFields) {
        comparators = new Comparator[sortFields.length];
        for (int i = 0; i < sortFields.length; i++) {
            Field sortField = sortFields[i];
                comparators[i] = (o1, o2) ->
                        org.globsframework.core.utils.Utils.compare((Comparable) o1.getValue(sortField),
                                (Comparable) o2.getValue(sortField));
        }
    }

    public int compare(FunctionalKey o1, FunctionalKey o2) {
        for (Comparator<FunctionalKey> comparator : comparators) {
            final int compare = comparator.compare(o1, o2);
            if (compare != 0) {
                return compare;
            }
        }
        return 0;
    }
}
