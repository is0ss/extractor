package org.archipel.extractors.protocol;

import org.archipel.asm.IMethodTransformer;
import org.archipel.util.ASMUtils;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;

import static org.archipel.extractors.protocol.PacketAnalyzer.BYTEBUF;

public class BooleanFixer implements IMethodTransformer {
	public static void transform(MethodNode mn) {
		var il = new InsnList();

		ASMUtils.replaceInstructionPattern(mn.instructions, il, 2, (i, ins) -> {
			int opcode = ins.getOpcode();
			return switch (i) {
				case -2 -> {
					il.add(new MethodInsnNode(INVOKESTATIC, BYTEBUF, "bitmask", "(II)Z"));
					System.out.print("IAND " + Printer.OPCODES[opcode] + "\n");
					if (opcode == IFLE && ins instanceof JumpInsnNode f) {
						il.add(new JumpInsnNode(opcode, f.label));
						yield true;
					}
					yield false;
				}
				case 1 -> opcode == IAND;
				case 2 -> opcode == IFEQ || opcode == IFNE || opcode == IFLE;
				default -> true; // delete all instructions
			};
		});

		ASMUtils.replaceInstructionPattern(mn.instructions, il, 7, (i, ins) -> {
			int opcode = ins.getOpcode();
			return switch (i) {
				case -1 -> {
					if (ins instanceof MethodInsnNode m && m.owner.equals(BYTEBUF)) {
						if (m.name.equals("bitmask"))
							yield false;
						if (m.name.equals("readUnsignedByte"))
							il.add(new MethodInsnNode(INVOKEVIRTUAL, BYTEBUF, "readBoolean", "()Z"));
						else
							System.out.print(m.name + "?");
					}
					yield true;
				}
				case 1 -> ins instanceof MethodInsnNode;
				case 2 -> opcode == IFEQ;
				// case 2 -> insn instanceof JumpInsnNode j && j.label.getNext().getOpcode() <= ICONST_1;
				case 3 -> opcode == ICONST_1;
				case 4 -> opcode == GOTO;
				case 5 -> ins instanceof LabelNode;
				case 6 -> ins instanceof FrameNode;
				case 7 -> opcode == ICONST_0;
				default -> true; // delete all instructions
			};
		});
	}
}
