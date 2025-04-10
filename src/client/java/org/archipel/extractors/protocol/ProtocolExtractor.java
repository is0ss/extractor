package org.archipel.extractors.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.archipel.Extractor;
import org.archipel.extractors.IExtractor;
import org.archipel.util.ReflectionUtils;
import org.objectweb.asm.tree.analysis.AnalyzerException;

import java.io.IOException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandleInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProtocolExtractor implements IExtractor {
	private static String packetFlowDirection(PacketFlow side) {
		return side == PacketFlow.CLIENTBOUND ? "s2c" : "c2s";
	}

	private static String stripPacketName(String name) {
		return (name.contains("bound") ? name.substring(11) : name).replace("Packet", ""); // "Serverbound" and "Clientbound" are both 11 characters
	}

	private record PacketObj(int id, Class<?> packet, MethodHandleInfo desInfo) {
		public JsonObject unwrap() throws AnalyzerException, IOException {
			final var TARGETS = Set.of(/* ClientIntentionPacket.class, */ ServerboundChatCommandPacket.class);
			var o = new JsonObject();

			o.addProperty("id", id);
			o.addProperty("name", stripPacketName(packet.getSimpleName()));
			if (TARGETS.contains(packet)) {
				var an = new PacketAnalyzer(packet);
				o.add("contents", an.analyze(desInfo));
				o.add("constants", an.getConstants());
			}
			return o;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public JsonElement extract() {
		var protocol = new JsonObject();
		int packetCount = 0;

		for(var state : ConnectionProtocol.values()) { // TODO: Handle the case where values aren't in the same order as their id?
			var flows = (Map<PacketFlow, ?/* extends PacketSet */>) ReflectionUtils.readField(state, "flows");
			var sides = new JsonObject();

			for(var side : PacketFlow.values()) {
				if (!flows.containsKey(side))
					continue; // skip if there are no packets on this side

				Extractor.LOGGER.debug(state + " " + side + ":");

				var deserializers = (List<?>) ReflectionUtils.readField(flows.get(side), "idToDeserializer");
				var packetMap = state.getPacketsByIds(side);
				var packets = new JsonArray();

				for(int i = 0; i < deserializers.size(); i++, packetCount++) {
					MethodHandleInfo desInfo = LambdaMetafactory.LAMBDA_CACHE.get(deserializers.get(i).getClass()); // the MAGIC
					try {
						packets.add(new PacketObj(i, packetMap.get(i), desInfo).unwrap());
					} catch (IOException | AnalyzerException e) {
						throw new IllegalStateException("Failed to analyze previous packet", e);
					}
				}

				sides.add(packetFlowDirection(side), packets);
			}

			protocol.add(state.name().toLowerCase(), sides);
		}

		Extractor.LOGGER.info("there were {} packets!", packetCount);
		protocol.add("types", PacketAnalyzer.getAdditionalTypes());
		return protocol;
	}

	@Override
	public String getName() {
		return "protocol";
	}
}
