package org.globsframework.shared.mem.impl.write;

import junit.framework.TestCase;
import org.junit.Assert;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

public class UtilsTest extends TestCase {

    public void testSplit1Element() {
        for (int i = 0; i < 20000; i++) {
            check1Element(0, i);
            check1Element(1, i);
            check1Element(3, i);
        }
    }

    public void testSplit3Element() {
        for (int i = 0; i < 20000; i++) {
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
            Assert.assertTrue(indices.contains(i));
        });
    }

    private void check3Element(int from, int to) {
        final Node3Elements split = Utils.split3Element(from, to);
        Set<Integer> indices = new HashSet<>();
        scan(split, indices);
        IntStream.range(from, to).forEach(i -> {
            Assert.assertTrue(indices.contains(i));
        });
    }

    private void scan(Node3Elements split, Set<Integer> indices) {
        if (split == null) {
            return;
        }
        if (split.indice1 != -1) {
            Assert.assertTrue("" + split.indice1, indices.add(split.indice1));
        }
        if (split.indice2 != -1) {
            Assert.assertTrue("" + split.indice2, indices.add(split.indice2));
        }
        if (split.indice3 != -1) {
            Assert.assertTrue("" + split.indice3, indices.add(split.indice3));
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
            Assert.assertTrue("" + split.indice1, indices.add(split.indice1));
        }
        scan(split.val1, indices);
        scan(split.val2, indices);
    }

}