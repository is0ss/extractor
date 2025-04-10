package org.archipel.extractors.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import org.archipel.Extractor;
import org.archipel.util.ASMUtils;
import org.archipel.util.ReflectionUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;
import org.objectweb.asm.util.Printer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandleInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collector;

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;

public class PacketAnalyzer extends Interpreter<PacketAnalyzer.PacketValue> implements Opcodes {
	public static final String BYTEBUF = Type.getInternalName(FriendlyByteBuf.class);
	public static final Map<String, JsonObject> ADDITIONAL_TYPES = new HashMap<>();

	private final Map<Object, Set<Method>> constants = new HashMap<>();
	private final JsonObject contents = new JsonObject();
	private int fieldCount = 0;
	private int readCount = 0;

	private List<? extends PacketValue> currentParams;
	private ClassReader baseCr;

	// Map<String, MethodHandle> exitPoints;

	/**
	 * @param origin Bytebuf method that created it
	 */
	public record PacketValue(String owner, String origin, List<? extends PacketValue> params) implements Value {
		public static final PacketValue BYTEBUF = new PacketValue(PacketAnalyzer.BYTEBUF, null, null);
		public static final PacketValue NO_ORIGIN = new PacketValue(null, null, null);

		@Override
		public int getSize() {
			return 1;
		}

		public String toString() {
			if (this == BYTEBUF) {
				return "BYTEBUF";
			}
			if (this == NO_ORIGIN) {
				return "NO_ORIGIN";
			}
			return owner + "." + origin + params;
		}
	}

	public PacketAnalyzer(Class<?> packet) {
		super(ASM9);

		Extractor.LOGGER.info("Analyzing {}...", packet);

		for (Field f : packet.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers())) {
				var field = new Method(f.getName(), Type.getDescriptor(f.getType())); // store field handles as methods for simplicity
				var value = ReflectionUtils.readStatic(f);

				value = value instanceof Short s ? s.intValue() : value instanceof Byte b ? b.intValue() : value;

				this.constants.computeIfPresent(value, (k, v) -> {
					v.add(field);
					Extractor.LOGGER.warn("{} have the same value?", v);
					return v;
				});

				this.constants.putIfAbsent(value, new HashSet<>(Set.of(field)));
			} else {
				this.fieldCount++; // count instance fields
			}
		}
	}

	/* get and read class from internal name */

	public static ClassReader readInternalClass(AbstractInsnNode ins, String internal) throws AnalyzerException {
		try {
			return new ClassReader(internal.replace('/', '.'));
		} catch (IOException e) {
			throw new AnalyzerException(ins, "owner not loaded", e);
		}
	}

	public static Class<?> getInternalClass(AbstractInsnNode ins, String internal) throws AnalyzerException {
		try {
			return Class.forName(internal.replace('/', '.'));
		} catch (ClassNotFoundException e) {
			throw new AnalyzerException(ins, "owner class not found", e);
		}
	}

	/* for recursive internal use */
	private PacketAnalyzer(AbstractInsnNode ins, String owner) throws AnalyzerException {
		this(getInternalClass(ins, owner));
		this.baseCr = readInternalClass(ins, owner);
	}

	public JsonObject getConstants() {
		return new JsonObject(); // TODO: read constants map converting between all the primitive types for addProperty
	}

	public static JsonArray getAdditionalTypes() {
		return ADDITIONAL_TYPES.values().stream()
				.collect(Collector.of(
						JsonArray::new,
						JsonArray::add,
						(l,r) -> {
							l.addAll(r);
							return l;
						}
				));
	}

	public JsonObject analyze(MethodHandleInfo deserializer) throws IOException, AnalyzerException {
		this.baseCr = new ClassReader(deserializer.getDeclaringClass().getName());
		return this.analyzeInternal(baseCr, deserializer.getModifiers(), deserializer.getName(), ReflectionUtils.getMethodDesc(deserializer), null);
	}

	private JsonObject analyzeInternal(ClassReader cr, int acc, String name, String desc, List<? extends PacketValue> params) throws AnalyzerException {
		var cw = new ClassWriter(cr, 0);
		var owner = cr.getClassName();

		var target = new MethodNode(ASM9, acc, name, desc, null, null) {
			@Override
			public void visitEnd() {
				BooleanFixer.transform(this);
				accept(mv); // new ConstantMapper(owner, this, constants, mv));
			}
		};

		ASMUtils.quickMethodTransform(cr, cw, EXPAND_FRAMES | SKIP_DEBUG, (n, d) -> n.equals(name) && d.equals(desc), target);

		var an = new Analyzer<>(this);
		this.currentParams = params;
		an.analyze(owner, target);

		// Files.write(Paths.get("./extracted/" + deserializer.getDeclaringClass().getSimpleName() + ".class"), cw.toByteArray());

		if (fieldCount != readCount)
			Extractor.LOGGER.warn("read packet fields mismatch (packet has {}, read {})", fieldCount, readCount);
		return this.contents;

		/* var frames = an.getFrames();
		Frame<PacketValue> frame;

		var ins = target.instructions.toArray();

		for (int i = 0; i < ins.length; i++) {
			if ((frame = frames[i]) == null) // instruction is never reached
				continue;
		} */
	}

	// ClientboundMapItemDataPacket
	// ClientboundSetEquipmentPacket
	// ClientboundBossEventPacket
	// ClientboundSetObjectivePacket

	// ClientboundStopSoundPacket

	@Override
	public PacketValue newValue(final Type type) {
		if (type == Type.VOID_TYPE)
			return null;
		return Type.getType(FriendlyByteBuf.class).equals(type) ? PacketValue.BYTEBUF : PacketValue.NO_ORIGIN;
	}

	@Override
	public PacketValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
		// if we are analyzing recursively pop the params from the start
		if (this.currentParams != null) {
			return currentParams.remove(0);
		}
		return newValue(type);
	}

	@Override
	public PacketValue newOperation(AbstractInsnNode ins) throws AnalyzerException {
		System.out.println(Printer.OPCODES[ins.getOpcode()]);
		return newValue(null);
	}

	@Override
	public PacketValue copyOperation(AbstractInsnNode ins, PacketValue v) throws AnalyzerException {
		System.out.println(Printer.OPCODES[ins.getOpcode()]);
		return v;
	}

	@Override
	public PacketValue unaryOperation(AbstractInsnNode ins, PacketValue v) throws AnalyzerException {
		System.out.println(Printer.OPCODES[ins.getOpcode()]);
		if (ins.getOpcode() != CHECKCAST)
			throw new AnalyzerException(ins, "unaryOperation");
		return v;
	}

	@Override
	public PacketValue binaryOperation(AbstractInsnNode ins, PacketValue v1, PacketValue v2) throws AnalyzerException {
		System.out.print(Printer.OPCODES[ins.getOpcode()] + " ");
		if (ins instanceof FieldInsnNode f) {
			if (f.getOpcode() == GETSTATIC) {
				System.out.print("STATIC " + f.name);
			}
			if (f.getOpcode() == PUTFIELD) {
				System.out.println(f.name + " " + v2);
				if (v2 != PacketValue.NO_ORIGIN) {
					this.contents.addProperty(f.name, v2.origin());
					this.readCount += 1;
				}
				return null;
			}
		}
		System.out.println();
		throw new AnalyzerException(ins, "binaryOperation");
	}

	@Override
	public PacketValue ternaryOperation(AbstractInsnNode ins, PacketValue v1, PacketValue v2, PacketValue v3) throws AnalyzerException {
		System.out.println(Printer.OPCODES[ins.getOpcode()] + " ");
		throw new AnalyzerException(ins, "ternaryOperation");
	}

	@Override
	public PacketValue naryOperation(AbstractInsnNode ins, List<? extends PacketValue> values) throws AnalyzerException {
		System.out.print(Printer.OPCODES[ins.getOpcode()] + " ");
		if (ins instanceof MethodInsnNode m) {
			var origins = values.stream().filter(v -> v != PacketValue.NO_ORIGIN).toList();
			System.out.println(m.owner + "." + m.name + m.desc + " " + origins);
			if (m.getOpcode() == INVOKESPECIAL && this.baseCr.getClassName().equals(m.owner))
				this.analyzeInternal(this.baseCr, 0, m.name, m.desc, values);
			if (m.owner.equals(BYTEBUF))
				return new PacketValue(m.owner, m.name, values);
			if (values.contains(PacketValue.BYTEBUF)) {
				var an = new PacketAnalyzer(ins, m.owner);
				ADDITIONAL_TYPES.put(m.owner, an.analyzeInternal(an.baseCr, 0, m.name, m.desc, values));
				return new PacketValue(m.owner, m.name, values);
			}
			if (origins.size() > 1 && m.getOpcode() != INVOKESPECIAL)
				throw new AnalyzerException(ins, "multiple origins for values, can't decide");
			if (m.getOpcode() == INVOKESTATIC && !origins.isEmpty())
				return origins.get(0);
		} else System.out.println();
		return newValue(null);
	}

	@Override
	public void returnOperation(AbstractInsnNode ins, PacketValue v, PacketValue expected) {
		System.out.println(Printer.OPCODES[ins.getOpcode()] + " ");
	} // nothing to do

	@Override
	public PacketValue merge(PacketValue v1, PacketValue v2) {
		throw new RuntimeException("merge");
	}
}
