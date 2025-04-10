package org.archipel.extractors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;

public class TypesExtractor implements IExtractor
{
	@Override
	public JsonElement extract()
	{
		final JsonObject types = new JsonObject();
		final JsonObject protocol = new JsonObject();
		final JsonObject enumConnectionProtocol = new JsonObject();

		for (final ConnectionProtocol value : ConnectionProtocol.values())
			enumConnectionProtocol.addProperty(value.name(), value.getId());

		protocol.add("enum", enumConnectionProtocol);
		protocol.addProperty("repr", "varint");

		types.add("ConnectionProtocol", protocol);

		final JsonObject sides = new JsonObject();
		final JsonObject enumPacketFlow = new JsonObject();
		for (final PacketFlow value : PacketFlow.values())
			enumPacketFlow.addProperty(value.name(), value.ordinal());
		sides.add("enum", enumPacketFlow);
		sides.addProperty("repr", "varint");
		types.add("PacketFlow", sides);

		return types;
	}

	@Override
	public String getName()
	{
		return "types";
	}
}
