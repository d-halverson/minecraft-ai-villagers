package com.drew.aivillagers;

import com.drew.aivillagers.config.AiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AiConfigTest {

	@Test
	public void testDefaultConfigValues(@TempDir Path tempDir) {
		AiConfig cfg = AiConfig.load(tempDir);
		assertNotNull(cfg.ollama);
		assertEquals("http://localhost:11434/api/chat", cfg.ollama.url);
		assertEquals("qwen2.5:0.5b", cfg.ollama.model);
		assertEquals(20, cfg.ollama.num_predict);
		assertEquals(3, cfg.ollama.num_thread);
		assertEquals(0.8, cfg.ollama.temperature, 0.001);

		assertNotNull(cfg.chat);
		assertEquals(8.0, cfg.chat.radius, 0.001);
		assertEquals(10, cfg.chat.cooldown_seconds);
		assertEquals(60, cfg.chat.max_reply_chars);
		assertEquals(5, cfg.chat.display_seconds);
		assertEquals(3, cfg.chat.max_queue);

		assertFalse(cfg.fallback_lines.isEmpty());
	}

	@Test
	public void testPersonaResolution(@TempDir Path tempDir) {
		AiConfig cfg = AiConfig.load(tempDir);

		// Per-villager custom persona
		String gareth = cfg.getPersona("Gareth");
		assertTrue(gareth.toLowerCase().contains("grumpy blacksmith"));

		// Case-insensitive lookup
		String garethLower = cfg.getPersona("gareth");
		assertEquals(gareth, garethLower);

		// Default persona replacement
		String albert = cfg.getPersona("Albert");
		assertTrue(albert.contains("Albert"));
		assertTrue(albert.contains("under 12 words"));
	}

	@Test
	public void testSaveAndReload(@TempDir Path tempDir) {
		AiConfig cfg = AiConfig.load(tempDir);
		cfg.chat.radius = 12.5;
		cfg.personas.put("farmer_joe", "You are Joe, a cheerful farmer.");
		cfg.save(tempDir);

		AiConfig reloaded = AiConfig.load(tempDir);
		assertEquals(12.5, reloaded.chat.radius, 0.001);
		assertEquals("You are Joe, a cheerful farmer.", reloaded.getPersona("farmer_joe"));
	}
}
