package org.archipel.util;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiPredicate;

public final class ASMUtils implements Opcodes {
	public static Optional<Handle> getLambdaImpl(Handle bsm, Object... args) {
		return bsm.getName().equals("metafactory") ?
			Arrays.stream(args)
				.filter(Handle.class::isInstance)
				.map(Handle.class::cast)
				.findFirst()
			: Optional.empty();
	}

	public static void replaceInstructionPattern(InsnList insns, InsnList newInsns, int patternLen, BiPredicate<Integer, AbstractInsnNode> matcher) {
		var it = insns.iterator();

		while (it.hasNext()) {
			var matched = false;

			for (int i = 1; i <= patternLen; i++) {
				if (it.hasNext() && !(matched = matcher.test(i, it.next())))
					break;
			}

			if (matched) {
				for (int i = -patternLen; i != 0; i++) {
					if (matcher.test(i, it.previous()))
						it.remove();
				}
				insns.insert(it.previous(), newInsns);
			}
		}
	}

	public static void quickMethodTransform(ClassReader cr, ClassVisitor cv, int flags, BiPredicate<String, String> filter, MethodVisitor mv) {
		cr.accept(new ClassVisitor(ASM9, cv) {
			@Override
			public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] exs) {
				var mv0 = super.visitMethod(acc, name, desc, sig, exs);

				if (filter.test(name, desc)) {
					ReflectionUtils.setField(mv, "mv", mv0);
					return mv;
				} else {
					return mv0;
				}
			}
		}, flags);
	}

	public static void insertMethodCall(MethodVisitor mv, int opcode, Class<?> c, String name, Class<?>... args) {
		mv.visitMethodInsn(opcode, Type.getInternalName(c), name, ReflectionUtils.getMethodDesc(c, name, args), opcode == INVOKEINTERFACE);
	}

	private static final Object[] OPCODE_CONSTANTS = {
		null, // ACONST_NULL
		-1,	// ICONST_M1
		0,	// ICONST_0
		1,	// ICONST_1
		2,	// ICONST_2
		3,	// ICONST_3
		4,	// ICONST_4
		5,	// ICONST_5
		0L,	// LCONST_0
		1L,	// LCONST_1
		0.0f,	// FCONST_0
		1.0f,	// FCONST_1
		2.0f,	// FCONST_2
		0.0,	// DCONST_0
		1.0 	// DCONST_1
	};

	public static boolean isConstant(int opcode) {
		return opcode >= ACONST_NULL && opcode <= DCONST_1;
	}

	public static Object getConstant(int opcode) {
		return OPCODE_CONSTANTS[opcode - ACONST_NULL]; // array starts at ACONST_NULL but it is not opcode 0
	}
}
