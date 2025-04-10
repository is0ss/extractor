package org.archipel.extractors.protocol;

import org.archipel.Extractor;
import org.archipel.asm.AnalyzerAdapter;
import org.archipel.util.ASMUtils;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;

import java.util.Map;
import java.util.Set;

import static org.archipel.extractors.protocol.PacketAnalyzer.BYTEBUF;

public final class ConstantMapper extends AnalyzerAdapter implements Opcodes {
	private final Map<Object, Set<Method>> constants;

	public ConstantMapper(String owner, MethodNode mn, Map<Object, Set<Method>> constants, MethodVisitor mv) {
		super(ASM9, owner, mn.access, mn.name, mn.desc, mv);
		this.constants = constants;
	}

	private boolean mapToConstantField(Object cst) {
		var hasMapping = constants.containsKey(cst);

		if (hasMapping) {
			constants.get(cst).forEach(f -> super.visitFieldInsn(GETSTATIC, owner, f.getName(), f.getDescriptor()));
		} else if (cst != null && !(cst instanceof Type || cst instanceof String)) {
			Extractor.LOGGER.warn("there is no constant associated to {}?", cst);
		}
		return hasMapping;
	}

	@Override
	public void visitJumpInsn(int opcode, Label label) {
		System.out.print(Printer.OPCODES[opcode] + " jmp instruction\n");
		if (opcode == IFEQ && !stack.get(stack.size() - 1).equals("Z") && mapToConstantField(0)) {
			opcode = IF_ICMPEQ;
		}
		super.visitJumpInsn(opcode, label);
	}

	@Override
	public void visitLdcInsn(Object value) {
		if (!mapToConstantField(value)) {
			super.visitLdcInsn(value);
		}
	}

	@Override
	public void visitIntInsn(int opcode, int operand) {
		if (opcode == NEWARRAY || !mapToConstantField(operand)) {
			super.visitIntInsn(opcode, operand);
		}
	}

	@Override
	public void visitInsn(int opcode) {
		if (!ASMUtils.isConstant(opcode) || !mapToConstantField(ASMUtils.getConstant(opcode))) {
			super.visitInsn(opcode);
		}
		if (opcode >= ISHL && opcode <= LXOR) {
			System.out.printf("bitwise %s instruction!\n", Printer.OPCODES[opcode]);
		}
	}

	/* @Override
	public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
		var own = owner.equals(this.owner) && opcode != INVOKESTATIC ? "this" : owner;

		for (int i = 1; i <= Type.getArgumentTypes(desc).length; i++) {
			if (stack.get(stack.size() - i).equals(BYTEBUF)) {
				System.out.print(own + "." + name + desc + " : " + stack + " takes bytebuf\n");
				super.visitMethodInsn(opcode, owner, name, desc, isInterface);
				return;
			}
		}

		if (owner.equals(BYTEBUF)) {
			System.out.print("bytebuf." + name + desc + " : " + stack + "\n");
		}

		if (owner.equals(this.owner) && opcode == INVOKESTATIC) {
			System.out.print(owner + "." + name + desc + " : " + stack + " external logic\n");
			super.visitMethodInsn(opcode, owner, name, desc, isInterface);
			return;
		}

		super.visitMethodInsn(opcode, owner, name, desc, isInterface);
	} */
}
