package org.globsframework.shared.mem;

public class Utils {
    static Node1Elements split1Element(int from, int to) {
        int len = to - from + 1; // from and to inclue
        if (len <= 0) {
            return null;
        }
        Node1Elements node = new Node1Elements();
        if (len == 1) {
            node.indice1 = from;
            return node;
        }
        int mid = from + len / 2;

        node.val1 = split1Element(from, mid - 1);
        node.indice1 = mid;
        node.val2 = split1Element(mid + 1, to);
        return node;
    }

    static Node3Elements split3Element(int from, int to) {
        int len = to - from + 1; // from and to inclue
        if (len <= 0) {
            return null;
        }
        Node3Elements node = new Node3Elements();
        if (len == 1) {
            node.indice1 = from;
            return node;
        } else if (len == 2) {
            node.indice1 = from;
            node.indice2 = from + 1;
            return node;
        } else if (len == 3) {
            node.indice1 = from;
            node.indice2 = from + 1;
            node.indice3 = from + 2;
            return node;
        }
        int mid = from + len / 2;

        int midLeft = from + (mid - from + 1) / 2;
        int midRight = mid + (to - mid + 1) / 2;
        node.val1 = split3Element(from, midLeft - 1);
        node.indice1 = midLeft;
        node.val2 = split3Element(midLeft + 1, mid - 1);
        node.indice2 = mid;
        node.val3 = split3Element(mid + 1, midRight - 1);
        node.indice3 = midRight;
        node.val4 = split3Element(midRight + 1, to);
        return node;
    }
}
