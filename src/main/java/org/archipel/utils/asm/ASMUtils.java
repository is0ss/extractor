package org.archipel.utils.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public final class ASMUtils {
    private static final Map<Integer, String> OPCODES = Arrays.stream(Opcodes.class.getFields())
            .dropWhile(f -> !f.getName().equals("NOP"))
            .collect(Collectors.toMap(
                    f -> {
                        try {
                            return f.getInt(null);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }, Field::getName
            ));

    public static InputStream loadClass(Class<?> cl) {
        return ClassLoader.getSystemResourceAsStream(cl.getName().replace('.', '/') + ".class");
    }

    public static boolean sameMethod(MethodNode mn, Method m) {
        return mn.name.equals(m.getName()) && mn.desc.equals(m.getDescriptor());
    }

    public static Method getMethod(Class<?> cl, String name, Class<?>... params) {
        try {
            return Method.getMethod(cl.getMethod(name, params));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getOpcodeString(int op) {
        return OPCODES.get(op);
    }

    public static String getInsnAsString(AbstractInsnNode insn) {
        if (insn instanceof LabelNode l)
            return l.getLabel().toString();
        if (insn instanceof LineNumberNode n)
            return String.valueOf(n.line);
        if (insn != null)
            return getOpcodeString(insn.getOpcode());
        else
            return "null";
    }

    public static String disassemble(AbstractInsnNode insn) {
        var str = new StringBuilder(getInsnAsString(insn)).append(' ');

        if (insn instanceof LabelNode)
            str.append(':');

        if (insn instanceof VarInsnNode v)
            str.append(v.var);

        if (insn instanceof FieldInsnNode f)
            str.append(String.format("%s : %s", f.name, f.desc));

        if (insn instanceof MethodInsnNode m)
            str.append(String.format("%s %s", m.name, m.desc));

        return str.toString();
    }
}
