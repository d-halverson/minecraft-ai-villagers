package com.drew.aivillagers;

import com.drew.aivillagers.config.AiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AiConfigTest {

	@Test
	public void testDefaultConfigValues(@TempDir Path tempDir) {
		AiConfig cfg = AiConfig.load(tempDir);
		assertNotNull(cfg.ollama);
		assertEquals("http://localhost:11434/api/chat", cfg.ollama.url);
		assertEquals("qwen2.5:0.5b", cfg.ollama.model);
		assertEquals(64, cfg.ollama.num_predict);
		assertEquals(AiConfig.CURRENT_VERSION, cfg.config_version);
		assertEquals(3, cfg.ollama.num_thread);
		assertEquals(0.8, cfg.ollama.temperature, 0.001);

		assertNotNull(cfg.chat);
		assertEquals(16.0, cfg.chat.radius, 0.001);
		assertTrue(cfg.chat.require_persona);
		assertTrue(cfg.chat.freeze_near_players);
		assertEquals(16.0, cfg.chat.freeze_radius, 0.001);
		assertEquals(2, cfg.chat.cooldown_seconds);
		assertEquals(150, cfg.chat.max_reply_chars);
		assertEquals(6, cfg.chat.display_seconds);
		assertEquals(4, cfg.chat.max_queue);

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
		assertFalse(cfg.hasCustomPersona("Albert"));

		// Style rules carry the word limit derived from max_reply_chars
		assertTrue(cfg.getUserPrompt("Albert", "Steve", " hi ").startsWith("Steve says: hi\n"));
		assertTrue(cfg.getUserPrompt("Albert", "Steve", "hi").contains("under " + cfg.maxReplyWords() + " words"));
		assertTrue(cfg.getSystemPrompt("Gareth").startsWith(gareth));
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

	@Test
	public void testSetAndRemovePersona(@TempDir Path tempDir) {
		AiConfig cfg = AiConfig.load(tempDir);
		assertFalse(cfg.hasCustomPersona("Elena"));

		cfg.setPersona("Elena", "You are Elena, a cheerful florist.", tempDir);
		assertTrue(cfg.hasCustomPersona("Elena"));
		assertTrue(cfg.hasCustomPersona("elena"));
		assertEquals("You are Elena, a cheerful florist.", cfg.getPersona("Elena"));

		// Verify persisted to disk
		AiConfig reloaded = AiConfig.load(tempDir);
		assertTrue(reloaded.hasCustomPersona("elena"));
		assertEquals("You are Elena, a cheerful florist.", reloaded.getPersona("Elena"));

		// Test removal
		boolean removed = reloaded.removePersona("Elena", tempDir);
		assertTrue(removed);
		assertFalse(reloaded.hasCustomPersona("Elena"));

		AiConfig reloadedAfterRemove = AiConfig.load(tempDir);
		assertFalse(reloadedAfterRemove.hasCustomPersona("Elena"));
	}

	@Test
	public void testNamesWithSpacesAndUnderscoresMatch(@TempDir Path tempDir) {
		AiConfig cfg = AiConfig.load(tempDir);
		cfg.setPersona("chris_hansen", "You are Chris Hansen.", tempDir);
		assertTrue(cfg.hasCustomPersona("Chris Hansen"));
		assertTrue(cfg.hasCustomPersona("CHRIS  HANSEN"));
		assertTrue(cfg.hasCustomPersona("§6Chris-Hansen"));
		assertEquals("You are Chris Hansen.", cfg.getPersona("Chris Hansen"));
		assertTrue(cfg.isAiVillagerName("Chris Hansen"));
		assertFalse(cfg.isAiVillagerName("Bob"));
		cfg.chat.require_persona = false;
		assertTrue(cfg.isAiVillagerName("Bob"));
		assertFalse(cfg.isAiVillagerName("   "));
	}

	@Test
	public void testMigratesUntouchedOldDefaults(@TempDir Path tempDir) throws Exception {
		// A 0.1.0 file: no config_version, short limits, a persona keyed with spaces, and one value the user changed
		Files.writeString(tempDir.resolve("aivillagers.json"), """
			{
			  "ollama": {"url": "http://localhost:11434/api/chat", "model": "qwen2.5:0.5b", "num_predict": 20, "num_thread": 2, "temperature": 0.8, "timeout_seconds": 8},
			  "chat": {"radius": 8.0, "cooldown_seconds": 10, "max_reply_chars": 60, "display_seconds": 5, "max_queue": 3},
			  "fallback_lines": ["Eh?"],
			  "default_persona": "You are a villager named {name}. You live in a Minecraft village. Reply in under 12 words. Stay in character. Never mention being an AI.",
			  "personas": {"gareth": "You are Gareth, a grumpy blacksmith villager. Reply in under 12 words.", "Chris Hansen": "You are Chris Hansen."}
			}
			""");
		AiConfig cfg = AiConfig.load(tempDir);
		assertEquals(AiConfig.CURRENT_VERSION, cfg.config_version);
		assertEquals(64, cfg.ollama.num_predict);
		assertEquals(2, cfg.ollama.num_thread, "user-changed values are kept");
		assertEquals(20, cfg.ollama.timeout_seconds);
		assertEquals(16.0, cfg.chat.radius, 0.001);
		assertEquals(150, cfg.chat.max_reply_chars);
		assertTrue(cfg.chat.require_persona);
		assertTrue(cfg.chat.freeze_near_players);
		assertEquals(2, cfg.chat.max_responders);
		assertEquals(List.of("Eh?"), cfg.fallback_lines);
		assertFalse(cfg.default_persona.contains("12 words"));
		assertEquals("You are Gareth, a grumpy blacksmith villager.", cfg.getPersona("Gareth"));
		assertEquals("You are Chris Hansen.", cfg.getPersona("chris_hansen"));

		// The upgrade is written back, and loading again changes nothing
		String saved = Files.readString(tempDir.resolve("aivillagers.json"));
		assertTrue(saved.contains("\"config_version\": " + AiConfig.CURRENT_VERSION));
		assertTrue(saved.contains("\"chris_hansen\""));
		AiConfig again = AiConfig.load(tempDir);
		assertEquals(saved, Files.readString(tempDir.resolve("aivillagers.json")));
		assertEquals(150, again.chat.max_reply_chars);
	}

	@Test
	public void testBrokenFileIsNotOverwritten(@TempDir Path tempDir) throws Exception {
		Path file = tempDir.resolve("aivillagers.json");
		Files.writeString(file, "{ \"personas\": { oops");
		AiConfig cfg = AiConfig.load(tempDir);
		assertNotNull(cfg.chat);
		assertEquals("{ \"personas\": { oops", Files.readString(file));
	}
}
