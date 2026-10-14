package com.drew.aivillagers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class AiConfig {
	private static final Logger LOG = LoggerFactory.getLogger("aivillagers-config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public OllamaConfig ollama = new OllamaConfig();
	public ChatConfig chat = new ChatConfig();
	public List<String> fallback_lines = new ArrayList<>(List.of(
		"Hmm... give me a moment.",
		"Eh? Say that again.",
		"I'm busy right now."
	));
	public String default_persona = "You are a villager named {name}. You live in a Minecraft village. Reply in under 12 words. Stay in character. Never mention being an AI.";
	public Map<String, String> personas = new LinkedHashMap<>(Map.of(
		"gareth", "You are Gareth, a grumpy blacksmith villager. Reply in under 12 words."
	));

	public static final class OllamaConfig {
		public String url = "http://localhost:11434/api/chat";
		public String model = "qwen2.5:0.5b";
		public int num_predict = 20;
		public int num_thread = 3;
		public double temperature = 0.8;
		public int timeout_seconds = 8;
	}

	public static final class ChatConfig {
		public double radius = 8.0;
		public int cooldown_seconds = 10;
		public int max_reply_chars = 60;
		public int display_seconds = 5;
		public int max_queue = 3;
	}

	public static AiConfig load(Path configDir) {
		Path file = configDir.resolve("aivillagers.json");
		AiConfig cfg = new AiConfig();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				AiConfig loaded = GSON.fromJson(reader, AiConfig.class);
				if (loaded != null) {
					cfg = loaded;
					cfg.ensureDefaults();
					LOG.info("Loaded config from {}", file);
					return cfg;
				}
			} catch (Exception e) {
				LOG.error("Failed to read config file {}. Using defaults.", file, e);
			}
		}
		cfg.save(configDir);
		return cfg;
	}

	public void save(Path configDir) {
		try {
			Files.createDirectories(configDir);
			Path file = configDir.resolve("aivillagers.json");
			try (Writer writer = Files.newBufferedWriter(file)) {
				GSON.toJson(this, writer);
			}
			LOG.info("Saved config to {}", file);
		} catch (IOException e) {
			LOG.error("Failed to save config to {}", configDir, e);
		}
	}

	private void ensureDefaults() {
		if (ollama == null) ollama = new OllamaConfig();
		if (chat == null) chat = new ChatConfig();
		if (fallback_lines == null || fallback_lines.isEmpty()) {
			fallback_lines = new ArrayList<>(List.of("Hmm... give me a moment.", "Eh? Say that again.", "I'm busy right now."));
		}
		if (default_persona == null || default_persona.isBlank()) {
			default_persona = "You are a villager named {name}. You live in a Minecraft village. Reply in under 12 words. Stay in character. Never mention being an AI.";
		}
		if (personas == null) personas = new LinkedHashMap<>();
	}

	public String getPersona(String villagerName) {
		if (villagerName == null) villagerName = "Villager";
		String lower = villagerName.trim().toLowerCase(Locale.ROOT);
		String custom = personas.get(lower);
		if (custom != null && !custom.isBlank()) {
			return custom;
		}
		return default_persona.replace("{name}", villagerName.trim());
	}

	public boolean hasCustomPersona(String villagerName) {
		if (villagerName == null) return false;
		String lower = villagerName.trim().toLowerCase(Locale.ROOT);
		return personas.containsKey(lower);
	}

	public synchronized void setPersona(String villagerName, String personaText, Path configDir) {
		if (villagerName == null || villagerName.isBlank() || personaText == null || personaText.isBlank()) {
			return;
		}
		String lower = villagerName.trim().toLowerCase(Locale.ROOT);
		personas.put(lower, personaText.trim());
		save(configDir);
	}

	public synchronized boolean removePersona(String villagerName, Path configDir) {
		if (villagerName == null || villagerName.isBlank()) return false;
		String lower = villagerName.trim().toLowerCase(Locale.ROOT);
		boolean removed = personas.remove(lower) != null;
		if (removed) {
			save(configDir);
		}
		return removed;
	}
}
