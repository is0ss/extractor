package org.archipel;

import org.archipel.asm.FieldAdder;
import org.archipel.util.ASMUtils;
import org.objectweb.asm.*;
import org.objectweb.asm.signature.SignatureWriter;

import java.io.IOException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandleInfo;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;

public class PatchGen implements Opcodes {
	private static final Type METHOD_HANDLE_INFO = Type.getType(MethodHandleInfo.class);
	private static final Type LAMBDA_META = Type.getType(LambdaMetafactory.class);
	private static final Type HASH_MAP = Type.getType(HashMap.class);

	private static final String INNER_META = "java.lang.invoke.InnerClassLambdaMetafactory";
	private static final String TARGET_FIELD = "LAMBDA_CACHE";

	private static FieldAdder addLambdaCache(ClassWriter cw) {
		var sig = new SignatureWriter();
		sig.visitClassType(Type.getInternalName(HashMap.class));

		var K = sig.visitTypeArgument('=');
		K.visitClassType(Type.getInternalName(Class.class));
		K.visitTypeArgument();
		K.visitEnd();

		var V = sig.visitTypeArgument('=');
		V.visitClassType(METHOD_HANDLE_INFO.getInternalName());
		V.visitEnd();

		sig.visitEnd();

		return new FieldAdder(cw, ACC_PUBLIC | ACC_STATIC | ACC_FINAL, TARGET_FIELD, HASH_MAP, sig.toString(), null, null);
	}

	private static void writeClass(ZipOutputStream jar, String internalName, byte[] bytecode) throws IOException {
		jar.putNextEntry(new ZipEntry(internalName + ".class"));
		jar.write(bytecode);
		jar.closeEntry();
	}

	public static void main(String[] args) {
		try (ZipOutputStream jar = new ZipOutputStream(Files.newOutputStream(Paths.get("patch.jar"), StandardOpenOption.CREATE))) {
			var lm = new ClassReader(LAMBDA_META.getClassName());
			var lw = new ClassWriter(lm, COMPUTE_MAXS);

			lm.accept(addLambdaCache(lw), 0);

			writeClass(jar, lm.getClassName(), lw.toByteArray());

			var im = new ClassReader(INNER_META);
			var iw = new ClassWriter(im, 0);

			ASMUtils.quickMethodTransform(im, iw, 0, (name, desc) -> name.equals("buildCallSite"), new MethodVisitor(Opcodes.ASM9) {
				@Override
				public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
					super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
					if (name.equals("spinInnerClass")) {
						super.visitInsn(Opcodes.DUP); // dup return value
						super.visitFieldInsn(GETSTATIC, LAMBDA_META.getInternalName(), TARGET_FIELD, HASH_MAP.getDescriptor()); // get our map
						super.visitInsn(SWAP); // reorder to call put on the map
						super.visitVarInsn(ALOAD, 0); // get method handle representing the content of the lambda
						super.visitFieldInsn(GETFIELD, INNER_META.replace('.', '/'), "implInfo", METHOD_HANDLE_INFO.getDescriptor());
						ASMUtils.insertMethodCall(this, INVOKEVIRTUAL, HashMap.class, "put", Object.class, Object.class);
						super.visitInsn(POP); // discard put return value
					}
				}
			});

			writeClass(jar, im.getClassName(), iw.toByteArray());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
