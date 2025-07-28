package org.globsframework.shared.mem.impl.write;

import org.globsframework.core.functional.FunctionalKey;
import org.globsframework.core.metamodel.fields.Field;
import org.globsframework.core.metamodel.fields.StringField;
import org.globsframework.core.model.Glob;

import java.util.Comparator;
import java.util.Map;

public class IndexFunctionalKeyComparator implements Comparator<FunctionalKey> {
    private final Map<String, Glob> allStrings;
    private final Comparator<FunctionalKey>[] comparators;

    public IndexFunctionalKeyComparator(Map<String, Glob> allStrings, Field[] sortFields) {
        this.allStrings = allStrings;
        comparators = new Comparator[sortFields.length];
        for (int i = 0; i < sortFields.length; i++) {
            Field sortField = sortFields[i];
//            if (sortField instanceof StringField stringField) {
//                comparators[i] = (o1, o2) -> {
//                    final Glob glob1 = this.allStrings.get(o1.get(stringField));
//                    final Glob glob2 = this.allStrings.get(o2.get(stringField));
//                    final int i1 = glob1.getNotNull(StringRefType.offset);
//                    final int i2 = glob2.getNotNull(StringRefType.offset);
//                    return Integer.compare(i1, i2);
//                };
//            } else {
                comparators[i] = (o1, o2) ->
                        org.globsframework.core.utils.Utils.compare((Comparable) o1.getValue(sortField),
                                (Comparable) o2.getValue(sortField));
//            }
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
