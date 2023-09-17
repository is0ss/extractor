package org.archipel.utils.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.*;
import java.util.stream.Collectors;

public final class TrackingInterpreter extends SourceInterpreter implements Opcodes {
    private final Map<FieldInsnNode, Set<SourceValue>> fields = new HashMap<>();
    private final Map<Integer, Set<SourceValue>> locals = new HashMap<>();

    private final MethodNode method;

    public TrackingInterpreter(MethodNode method) {
        super(ASM9);
        this.method = method;
    }

    private void trackFieldAccess(FieldInsnNode f, SourceValue v) {
        fields.computeIfAbsent(f, k -> new HashSet<>()).add(v);
    }

    private <T> void inheritValues(Map<T, Set<SourceValue>> map, SourceValue value, SourceValue... others) {
        map.values().forEach(values -> {
            for (var o : others) {
                for (var v : values) {
                    if (System.identityHashCode(o) == System.identityHashCode(v)) {
                        values.add(value);
                        break;
                    }
                }
            }
        });
    }

    private void inheritAccess(SourceValue v, SourceValue... others) {
        inheritValues(fields, v, others);

        /* locals.forEach((l, values) -> {
            System.out.print(l.getDescriptor() + " ->");
            values.forEach(vv -> System.out.print(String.format(" %#x", System.identityHashCode(vv))));
            System.out.println();
        }); */

        inheritValues(locals, v, others);
    }

    private <T> Set<T> findFor(Map<T, Set<SourceValue>> map, SourceValue v) {
        Set<T> items = new HashSet<>();

        map.forEach((i, values) -> {
            if (values.contains(v))
                items.add(i);
        });

        return items;
    }

    public Set<FieldInsnNode> fields(SourceValue v) {
        return findFor(fields, v);
    }

    public Set<LocalVariableNode> locals(SourceValue v) {
        return findFor(locals, v).stream()
                .map(i -> method.localVariables.get(i))
                .collect(Collectors.toSet());
    }

    @Override
    public SourceValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
        var v =  super.newParameterValue(isInstanceMethod, local, type);
        // Main.LOGGER.info(String.format("param %#x %s", System.identityHashCode(v), type.getDescriptor()));
        locals.computeIfAbsent(local, k -> new HashSet<>()).add(v);
        return v;
    }

    @Override
    public SourceValue newOperation(AbstractInsnNode insn) {
        var v = super.newOperation(insn);

        if (insn instanceof FieldInsnNode f)
            trackFieldAccess(f, v);

        return v;
    }

    @Override
    public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
        var v = super.copyOperation(insn, value);
        // Main.LOGGER.info(String.format("copy %#x -> %#x", System.identityHashCode(value), System.identityHashCode(v)));
        inheritAccess(v, value);
        return v;
    }

    @Override
    public SourceValue unaryOperation(AbstractInsnNode insn, SourceValue value) {
        var v = super.unaryOperation(insn, value);

        if (insn instanceof FieldInsnNode f)
            trackFieldAccess(f, v);
        else
            inheritAccess(v, value);

        return v;
    }

    @Override
    public SourceValue binaryOperation(AbstractInsnNode insn, SourceValue value1, SourceValue value2) {
        var v = super.binaryOperation(insn, value1, value2);
        inheritAccess(v, value1, value2);
        return v;
    }

    @Override
    public SourceValue ternaryOperation(AbstractInsnNode insn, SourceValue value1, SourceValue value2, SourceValue value3) {
        var v = super.ternaryOperation(insn, value1, value2, value3);
        inheritAccess(v, value1, value2, value3);
        return v;
    }

    @Override
    public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
        var v = super.naryOperation(insn, values);
        values.forEach(o -> inheritAccess(v, o));
        return v;
    }

    @Override
    public SourceValue merge(SourceValue value1, SourceValue value2) {
        var v = super.merge(value1, value2);
        inheritAccess(v, value1, value2);
        return v;
    }
}
