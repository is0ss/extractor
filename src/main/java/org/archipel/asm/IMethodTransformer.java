package org.archipel.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

public interface IMethodTransformer extends Opcodes {
	static void transform(MethodNode cn) {}
}
