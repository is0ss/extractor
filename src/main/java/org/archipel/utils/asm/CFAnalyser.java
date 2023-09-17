package org.archipel.utils.asm;

import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

public final class CFAnalyser<V extends Value> extends Analyzer<V> {
    public CFAnalyser(Interpreter<V> interpreter) {
        super(interpreter);
    }

    @Override
    protected Node<V> newFrame(int numLocals, int numStack) {
        return new Node<>(numLocals, numStack);
    }

    @Override
    protected Frame<V> newFrame(Frame<? extends V> frame)
    {
        return new Node<>(frame);
    }

    @Override
    protected void newControlFlowEdge(int src, int dst) {
        ((Node<V>)this.getFrames()[src]).successors.add(dst);
    }
}
