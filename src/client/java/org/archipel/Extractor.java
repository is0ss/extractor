package org.archipel;

import com.google.gson.GsonBuilder;
import net.minecraft.server.Bootstrap;
import org.archipel.extractors.IExtractor;
import org.archipel.extractors.protocol.ProtocolExtractor;
import org.archipel.util.ReflectionUtils;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Extractor implements Opcodes {
	public static final Logger LOGGER = LoggerFactory.getLogger("extractor");

	private static final IExtractor[] EXTRACTORS = {
		// new TypesExtractor(),
		new ProtocolExtractor()
	};

	public static void main(String[] strings) throws IOException {
		LOGGER.info("Starting extraction...");

		ReflectionUtils.setStatic(Bootstrap.class, "isBootstrapped", true);

		var outputDir = Files.createDirectories(Paths.get("extracted"));
		var gson = new GsonBuilder().setPrettyPrinting().create();

		for(var ext : EXTRACTORS) {
			var name = ext.getName();
			try (var writer = Files.newBufferedWriter(outputDir.resolve(name + ".json"))) {
				gson.toJson(ext.extract(), writer);
				LOGGER.info("... extracted {}", name);
			} catch (Throwable t) {
				LOGGER.error("Failed to extract {}", name, t);
			}
		}
	}
}
