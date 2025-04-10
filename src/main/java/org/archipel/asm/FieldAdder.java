package org.archipel.asm;

import org.objectweb.asm.*;

import java.util.function.Function;

public class FieldAdder extends ClassVisitor implements Opcodes {
	private final Function<MethodVisitor, String> staticInit;
	private final Object fvalue;
	private final String fname;
	private final String fsig;
	private final Type ftype;
	private final int facc;

	private String owner;

	public FieldAdder(ClassVisitor cv, int acc, String name, Type type, String sig, Object value, Function<MethodVisitor, String> staticInit) {
		super(ASM9, cv);
		this.staticInit = staticInit != null ? staticInit : mv -> "()V";
		this.fvalue = value;
		this.fname = name;
		this.ftype = type;
		this.facc = acc;
		this.fsig = sig;
	}

	@Override
	public void visit(int ver, int acc, String name, String sig, String superName, String[] interfaces) {
		super.visit(ver, acc, name, sig, superName, interfaces);
		super.visitField(facc, fname, ftype.getDescriptor(), fsig, fvalue);
		this.owner = name;
	}

	@Override
	public MethodVisitor visitMethod(int acc, String name, String desc, String sig, String[] exs) {
		var mv = super.visitMethod(acc, name, desc, sig, exs);
		final int shouldInit = ACC_STATIC | ACC_FINAL;

		if ((facc & shouldInit) == shouldInit && fvalue == null && name.equals("<clinit>")) {
			return new MethodVisitor(ASM9, mv) {
				@Override
				public void visitCode() {
					super.visitCode();
					mv.visitTypeInsn(NEW, ftype.getInternalName());
					mv.visitInsn(DUP);
					mv.visitMethodInsn(INVOKESPECIAL, ftype.getInternalName(), "<init>", staticInit.apply(mv), false);
					mv.visitFieldInsn(PUTSTATIC, /* this. */ owner, fname, ftype.getDescriptor());
				}
			};
		}
		return mv;
	}
}
