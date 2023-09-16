package org.archipel.utils.asm;

import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Value;

import java.util.HashSet;
import java.util.Set;

public class Node<V extends Value> extends Frame<V> {
    public Set<Integer> successors = new HashSet<>();

    public Node(int numLocals, int maxStack) {
        super(numLocals, maxStack);
    }

    public Node(Frame<? extends V> frame) {
        super(frame);
    }
}
