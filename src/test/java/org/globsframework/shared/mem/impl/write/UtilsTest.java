package org.globsframework.shared.mem.impl.write;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

public class UtilsTest {

    @Test
    public void testSplit1Element() {
        for (int i = 0; i < 10000; i++) {
            check1Element(0, i);
            check1Element(1, i);
            check1Element(3, i);
        }
    }

    @Test
    public void testSplit3Element() {
        for (int i = 0; i < 10000; i++) {
            check3Element(0, i);
            check3Element(1, i);
            check3Element(3, i);
        }
    }

    private void check1Element(int from, int to) {
        final Node1Elements split = Utils.split1Element(from, to);
        Set<Integer> indices = new HashSet<>();
        scan(split, indices);
        IntStream.range(from, to).forEach(i -> {
            Assertions.assertTrue(indices.contains(i));
        });
    }

    private void check3Element(int from, int to) {
        final Node3Elements split = Utils.split3Element(from, to);
        Set<Integer> indices = new HashSet<>();
        scan(split, indices);
        IntStream.range(from, to).forEach(i -> {
            Assertions.assertTrue(indices.contains(i));
        });
    }

    private void scan(Node3Elements split, Set<Integer> indices) {
        if (split == null) {
            return;
        }
        if (split.indice1 != -1) {
            Assertions.assertTrue(indices.add(split.indice1), "" + split.indice1);
        }
        if (split.indice2 != -1) {
            Assertions.assertTrue(indices.add(split.indice2), "" + split.indice2);
        }
        if (split.indice3 != -1) {
            Assertions.assertTrue(indices.add(split.indice3), "" + split.indice3);
        }
        scan(split.val1, indices);
        scan(split.val2, indices);
        scan(split.val3, indices);
        scan(split.val4, indices);
    }

    private void scan(Node1Elements split, Set<Integer> indices) {
        if (split == null) {
            return;
        }
        if (split.indice1 != -1) {
            Assertions.assertTrue(indices.add(split.indice1), "" + split.indice1);
        }
        scan(split.val1, indices);
        scan(split.val2, indices);
    }

}