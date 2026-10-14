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
import java.nio.file.StandardCopyOption;
import java.util.*;

public final class AiConfig {
	private static final Logger LOG = LoggerFactory.getLogger("aivillagers-config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Bumped when defaults change, so {@link #migrate()} can upgrade untouched old values in existing files. */
	public static final int CURRENT_VERSION = 2;

	private static final String DEFAULT_PERSONA = "You are a villager named {name}. You live in a Minecraft village.";
	private static final String DEFAULT_STYLE_RULES = "Stay in character as {name}. Never mention being an AI.";
	private static final String DEFAULT_REPLY_HINT = "(Reply as {name} in one or two short sentences, under {words} words.)";
	private static final List<String> DEFAULT_FALLBACKS = List.of("Hmm... give me a moment.", "Eh? Say that again.", "I'm busy right now.");

	/** 0 when the file predates versioning (0.1.0). New files are written with {@link #CURRENT_VERSION}. */
	public int config_version = 0;
	public OllamaConfig ollama = new OllamaConfig();
	public ChatConfig chat = new ChatConfig();
	public List<String> fallback_lines = new ArrayList<>(DEFAULT_FALLBACKS);
	/** Used for named villagers without a custom persona, only when {@code chat.require_persona} is false. */
	public String default_persona = DEFAULT_PERSONA;
	/** Appended to every persona's system prompt. */
	public String style_rules = DEFAULT_STYLE_RULES;
	/**
	 * Appended to the player's message. Small models follow a length limit far better here than in the system
	 * prompt, which keeps even long book-written personas inside {@code max_reply_chars}.
	 */
	public String reply_hint = DEFAULT_REPLY_HINT;
	public Map<String, String> personas = new LinkedHashMap<>(Map.of(
		"gareth", "You are Gareth, a grumpy blacksmith villager."
	));

	public static final class OllamaConfig {
		public String url = "http://localhost:11434/api/chat";
		public String model = "qwen2.5:0.5b";
		public int num_predict = 64;
		public int num_thread = 3;
		public double temperature = 0.8;
		public int timeout_seconds = 20;
	}

	public static final class ChatConfig {
		/** Villagers with a persona answer any chat message sent within this many blocks. */
		public double radius = 16.0;
		/** Only villagers whose name has a custom persona are AI villagers. */
		public boolean require_persona = true;
		/** Most villagers that answer one message (nearest first). Villagers named in the message take priority. */
		public int max_responders = 2;
		public int cooldown_seconds = 2;
		public int max_reply_chars = 150;
		/** Minimum bubble time; longer replies stay up longer (about one extra second per 15 characters). */
		public int display_seconds = 6;
		public int max_queue = 4;
		/** AI villagers stand still while a player is within {@code freeze_radius} blocks. */
		public boolean freeze_near_players = true;
		public double freeze_radius = 16.0;
	}

	public static AiConfig load(Path configDir) {
		Path file = configDir.resolve("aivillagers.json");
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file)) {
				AiConfig loaded = GSON.fromJson(reader, AiConfig.class);
				if (loaded != null) {
					boolean changed = loaded.ensureDefaults();
					changed |= loaded.migrate();
					if (changed) loaded.save(configDir);
					LOG.info("Loaded config from {}", file);
					return loaded;
				}
			} catch (Exception e) {
				// Leave a broken file alone so the user's personas are not overwritten with defaults
				LOG.error("Failed to read config file {}. Using defaults until it is fixed.", file, e);
				AiConfig fallback = new AiConfig();
				fallback.config_version = CURRENT_VERSION;
				return fallback;
			}
		}
		AiConfig cfg = new AiConfig();
		cfg.config_version = CURRENT_VERSION;
		cfg.save(configDir);
		return cfg;
	}

	public synchronized void save(Path configDir) {
		try {
			Files.createDirectories(configDir);
			Path file = configDir.resolve("aivillagers.json");
			Path tmp = configDir.resolve("aivillagers.json.tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(this, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			LOG.info("Saved config to {}", file);
		} catch (IOException e) {
			LOG.error("Failed to save config to {}", configDir, e);
		}
	}

	/** Fills in sections missing from older or hand-edited files. Returns true if anything changed. */
	private boolean ensureDefaults() {
		boolean changed = false;
		if (ollama == null) { ollama = new OllamaConfig(); changed = true; }
		if (chat == null) { chat = new ChatConfig(); changed = true; }
		if (fallback_lines == null || fallback_lines.isEmpty()) { fallback_lines = new ArrayList<>(DEFAULT_FALLBACKS); changed = true; }
		if (default_persona == null || default_persona.isBlank()) { default_persona = DEFAULT_PERSONA; changed = true; }
		if (style_rules == null) { style_rules = DEFAULT_STYLE_RULES; changed = true; }
		if (reply_hint == null) { reply_hint = DEFAULT_REPLY_HINT; changed = true; }
		if (personas == null) { personas = new LinkedHashMap<>(); changed = true; }

		// Re-key personas so "Chris Hansen", "chris_hansen" and "CHRIS HANSEN" are the same villager
		Map<String, String> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : personas.entrySet()) {
			String key = normalizeName(e.getKey());
			if (!key.isEmpty() && e.getValue() != null && !e.getValue().isBlank()) normalized.put(key, e.getValue().trim());
		}
		if (!normalized.equals(personas)) changed = true;
		personas = normalized;
		return changed;
	}

	/**
	 * Upgrades values that still equal an older release's defaults; values the user changed are left alone.
	 * Options missing from an old file already hold their new defaults (Gson keeps field initializers).
	 * Returns true if anything changed.
	 */
	boolean migrate() {
		if (config_version >= CURRENT_VERSION) return false;
		if (config_version < 2) {
			// 0.1.0 cut replies off mid-sentence (20 tokens, 60 chars) and needed the exact name within 8 blocks
			if (ollama.num_predict == 20) ollama.num_predict = 64;
			if (ollama.timeout_seconds == 8) ollama.timeout_seconds = 20;
			if (chat.radius == 8.0) chat.radius = 16.0;
			if (chat.cooldown_seconds == 10) chat.cooldown_seconds = 2;
			if (chat.max_reply_chars == 60) chat.max_reply_chars = 150;
			if (chat.display_seconds == 5) chat.display_seconds = 6;
			if (chat.max_queue == 3) chat.max_queue = 4;
			if (default_persona.equals("You are a villager named {name}. You live in a Minecraft village. Reply in under 12 words. Stay in character. Never mention being an AI.")) {
				default_persona = DEFAULT_PERSONA;
			}
			if ("You are Gareth, a grumpy blacksmith villager. Reply in under 12 words.".equals(personas.get("gareth"))) {
				personas.put("gareth", "You are Gareth, a grumpy blacksmith villager.");
			}
		}
		LOG.info("Upgraded config from version {} to {}", config_version, CURRENT_VERSION);
		config_version = CURRENT_VERSION;
		return true;
	}

	/** Lowercase, legacy formatting codes removed, and runs of spaces/underscores/dashes collapsed to one underscore. */
	public static String normalizeName(String name) {
		if (name == null) return "";
		String s = name.replaceAll("§.", "").trim().toLowerCase(Locale.ROOT);
		s = s.replaceAll("[\\s_\\-]+", "_");
		return s.replaceAll("^_+|_+$", "");
	}

	public String getPersona(String villagerName) {
		if (villagerName == null || villagerName.isBlank()) villagerName = "Villager";
		String custom = personas.get(normalizeName(villagerName));
		if (custom != null && !custom.isBlank()) {
			return custom;
		}
		return default_persona.replace("{name}", villagerName.trim());
	}

	/** The system prompt: persona plus {@code style_rules}. */
	public String getSystemPrompt(String villagerName) {
		String name = displayName(villagerName);
		String rules = fill(style_rules, name);
		String persona = getPersona(name);
		return rules.isBlank() ? persona : persona + "\n" + rules;
	}

	/** The user message: who said what, plus {@code reply_hint} with the length limit. */
	public String getUserPrompt(String villagerName, String playerName, String message) {
		String hint = fill(reply_hint, displayName(villagerName));
		String line = playerName + " says: " + message.trim();
		return hint.isBlank() ? line : line + "\n" + hint;
	}

	private static String displayName(String villagerName) {
		return villagerName == null || villagerName.isBlank() ? "Villager" : villagerName.trim();
	}

	private String fill(String template, String name) {
		return template == null ? "" : template.replace("{name}", name).replace("{words}", Integer.toString(maxReplyWords()));
	}

	/** Roughly six characters per English word, including the space. */
	public int maxReplyWords() {
		return Math.max(5, chat.max_reply_chars / 6);
	}

	public boolean hasCustomPersona(String villagerName) {
		if (villagerName == null) return false;
		return personas.containsKey(normalizeName(villagerName));
	}

	/** Whether a villager with this name tag talks back, according to {@code chat.require_persona}. */
	public boolean isAiVillagerName(String villagerName) {
		if (normalizeName(villagerName).isEmpty()) return false;
		return !chat.require_persona || hasCustomPersona(villagerName);
	}

	public synchronized void setPersona(String villagerName, String personaText, Path configDir) {
		String key = normalizeName(villagerName);
		if (key.isEmpty() || personaText == null || personaText.isBlank()) {
			return;
		}
		personas.put(key, personaText.trim());
		save(configDir);
	}

	public synchronized boolean removePersona(String villagerName, Path configDir) {
		String key = normalizeName(villagerName);
		if (key.isEmpty()) return false;
		boolean removed = personas.remove(key) != null;
		if (removed) {
			save(configDir);
		}
		return removed;
	}
}
