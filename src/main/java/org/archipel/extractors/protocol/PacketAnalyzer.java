package org.archipel.extractors.protocol;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import org.apache.commons.lang3.tuple.Pair;
import org.archipel.Main;
import org.archipel.utils.asm.ASMUtils;
import org.archipel.utils.asm.CFAnalyser;
import org.archipel.utils.asm.Node;
import org.archipel.utils.asm.TrackingInterpreter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.IOException;
import java.util.NoSuchElementException;

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;

public final class PacketAnalyzer {
    private static final Method TARGET = ASMUtils.getMethod(Packet.class, "write", PacketByteBuf.class);

    public static Multimap<String, Pair<String, String>> packetToFieldsMap = ArrayListMultimap.create();

    public static String analyze(Class<? extends Packet<?>> packet) {
        var obj = new JsonObject();

        try(var in = ASMUtils.loadClass(packet)) {
            var cr = new ClassReader(in);
            var cn = new ClassNode();

            cr.accept(cn, EXPAND_FRAMES);

            var writeMethod = cn.methods.stream()
                    .filter(m -> ASMUtils.sameMethod(m, TARGET))
                    .findFirst()
                    .orElseThrow();

            var inter = new TrackingInterpreter(writeMethod);
            var a = new CFAnalyser<>(inter);

            a.analyze(Type.getInternalName(packet), writeMethod);

            var insns = writeMethod.instructions.toArray();
            var nodes = a.getFrames();

            /* for (Frame<SourceValue> node : nodes) {
                if (node instanceof Node<?> n && n.successors.size() > 1) {
                    Main.LOGGER.info(packet.getName() + " needs special process");
                    break;
                }
            } */

            Main.LOGGER.info(packet.getName() + ":");

            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] != null)
                    analyzeFrame(packet, inter, insns[i], (Node<SourceValue>)nodes[i]);
            }
        } catch (IOException | NoSuchElementException | AnalyzerException e) {
            Main.LOGGER.error("Failed to analyze " + packet, e);
        }

        return packet.getSimpleName().replace("C2SPacket", "").replace("S2CPacket", "");
    }

    private static void analyzeFrame(Class<? extends Packet<?>> packet, TrackingInterpreter inter, AbstractInsnNode insn, Node<SourceValue> n) throws AnalyzerException {
        if (insn instanceof MethodInsnNode m) {
            Main.LOGGER.info(m.name + ':');
            // List<Type> argTypes = new ArrayList<>(List.of(Type.getArgumentTypes(m.desc)));
            // Collections.reverse(argTypes);

            FieldInsnNode field = null;
            var bufArg = false;

            for (int i = 0; i < Type.getArgumentTypes(m.desc).length + 1; i++) {
                var stack = n.getStack((n.getStackSize() - 1) - i);

                var fields = inter.fields(stack);
                var locals = inter.locals(stack);

                if (fields.size() > 0)
                    field = fields.toArray(new FieldInsnNode[0])[0];
                if (fields.size() > 1)
                    throw new AnalyzerException(insn, "invalid number of fields");

                if (locals.size() > 0)
                    bufArg = locals.toArray(new LocalVariableNode[0])[0].desc.equals(Type.getDescriptor(PacketByteBuf.class));
                if (fields.size() > 1)
                    throw new AnalyzerException(insn, "invalid number of locals");
            }

            if (bufArg) {
                packetToFieldsMap.put(packet.getName(), Pair.of(field != null ? field.name : "null", m.name + m.desc));
            }
        }

        /*
            var hasBranch = false;

            for (int i = 0; i < frms.length; i++) {
                if (frms[i] instanceof Node<?> n && n.successors.size() > 1) {
                    Main.LOGGER.info(i + " " + ASMUtils.disassemble(insns[i]));
                    n.successors.forEach(s -> Main.LOGGER.info("\t" + s + "-> " + ASMUtils.disassemble(insns[s])));
                }
            }

            /* if (hasBranch) {
                for (int i = 0; i < frms.length; i++) {
                    // Main.LOGGER.info("\t" + ASMUtils.disassemble(insns[i]));
                    //analyzeFrame(inter, insns[i], (Node<SourceValue>)frms[i]);
                }
            } */

        /* if (insn instanceof MethodInsnNode m) {
            Main.LOGGER.info(m.name + " " + m.desc);

            for (int i = 0; i < fr.getStackSize(); i++) {
                var v = fr.getStack(i);

                var nF = inter.fields(v).size();
                var nL = inter.locals(v).size();

                if (nF > 1) {
                    Main.LOGGER.info(nF + " fields");
                    inter.fields(v).forEach(f -> Main.LOGGER.info(String.format("%#x %s %s", System.identityHashCode(v), f.name, f.desc)));
                }

                if (nL > 1) {
                    Main.LOGGER.info(nL + " locals");
                    inter.locals(v).forEach(t -> Main.LOGGER.info(String.format("%#x %s", System.identityHashCode(v), t.name)));
                }
            }
        } */
    }
}
