package org.archipel.util;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandleInfo;
import java.lang.reflect.Field;

public class ReflectionUtils {
	public static Object readStatic(Field f) {
		try {
			return FieldUtils.readStaticField(f, true);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static Object readField(Object o, String name) {
		try {
			return FieldUtils.readField(o, name, true);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static void setStatic(Class<?> c, String field, Object v) {
		try {
			FieldUtils.writeStaticField(c, field, v, true);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static void setField(Object o, String name, Object v) {
		try {
			FieldUtils.writeField(o, name, v, true);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static String getMethodDesc(Class<?> c, String name, Class<?>... args) {
		return Type.getMethodDescriptor(MethodUtils.getMatchingMethod(c, name, args));
	}

	public static String getMethodDesc(MethodHandleInfo info) {
		return info.getMethodType().toMethodDescriptorString();
	}
}
