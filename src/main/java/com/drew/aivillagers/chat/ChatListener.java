package com.drew.aivillagers.chat;

import com.drew.aivillagers.bubble.BubbleManager;
import com.drew.aivillagers.config.AiConfig;
import com.drew.aivillagers.ollama.OllamaClient;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Any chat message makes the nearby AI villagers (named villagers with a persona) answer in a speech bubble.
 * The message does not have to contain a villager's name; naming one only picks who answers.
 */
public final class ChatListener {
	private static final Logger LOG = LoggerFactory.getLogger("aivillagers-chat");

	private final Supplier<AiConfig> config;
	private final OllamaClient ollama;
	private final BubbleManager bubbles;
	private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
	/** Villagers waiting for a reply; they ignore further chat until it arrives. */
	private final Set<UUID> busy = ConcurrentHashMap.newKeySet();
	private final AtomicInteger pendingCount = new AtomicInteger(0);
	private final Random random = new Random();

	public ChatListener(Supplier<AiConfig> config, OllamaClient ollama, BubbleManager bubbles) {
		this.config = config;
		this.ollama = ollama;
		this.bubbles = bubbles;
	}

	public void onChatMessage(PlayerChatMessage message, ServerPlayer player) {
		if (message == null) return;
		handle(player, message.signedContent());
	}

	/** Runs on the server thread. Returns the villagers that will answer (empty if none are in range). */
	public List<AbstractVillager> handle(ServerPlayer player, String chatText) {
		if (player == null || player.isSpectator() || chatText == null || chatText.isBlank()) {
			return List.of();
		}
		AiConfig cfg = config.get();

		long now = System.currentTimeMillis();
		Long lastTime = cooldowns.get(player.getUUID());
		if (lastTime != null && (now - lastTime) < cfg.chat.cooldown_seconds * 1000L) {
			return List.of();
		}

		List<AbstractVillager> responders = pickResponders(player, chatText, cfg);
		if (responders.isEmpty()) {
			return List.of();
		}
		cooldowns.put(player.getUUID(), now);

		for (AbstractVillager villager : responders) {
			villager.getLookControl().setLookAt(player, 30.0f, 30.0f);

			if (pendingCount.get() >= cfg.chat.max_queue) {
				bubbles.show(villager, randomFallback(cfg), cfg.chat.display_seconds);
				continue;
			}

			String name = villager.getCustomName().getString().trim();
			busy.add(villager.getUUID());
			pendingCount.incrementAndGet();
			bubbles.show(villager, "...", cfg.ollama.timeout_seconds + 5);

			String userPrompt = cfg.getUserPrompt(name, player.getName().getString(), chatText);
			ollama.chat(cfg.getSystemPrompt(name), userPrompt, cfg.ollama).whenComplete((reply, err) -> {
				String text = null;
				if (err == null && reply != null) {
					text = ReplyFormatter.fit(reply.text(), cfg.chat.max_reply_chars, reply.cutOff(), name);
				}
				if (text == null || text.isBlank()) {
					text = randomFallback(cfg);
				}
				final String bubbleText = text;
				player.level().getServer().execute(() -> {
					pendingCount.decrementAndGet();
					busy.remove(villager.getUUID());
					if (villager.isAlive() && !villager.isRemoved()) {
						bubbles.show(villager, bubbleText, displaySeconds(bubbleText, cfg));
					}
				});
			});
		}
		return responders;
	}

	/** AI villagers in range that are not already thinking; named ones first, otherwise the nearest. */
	private List<AbstractVillager> pickResponders(ServerPlayer player, String chatText, AiConfig cfg) {
		double r = cfg.chat.radius;
		double r2 = r * r;
		List<AbstractVillager> nearby = new ArrayList<>(player.level().getEntitiesOfClass(AbstractVillager.class,
			player.getBoundingBox().inflate(r),
			v -> v.isAlive() && v.hasCustomName() && v.distanceToSqr(player) <= r2
				&& !busy.contains(v.getUUID()) && cfg.isAiVillagerName(v.getCustomName().getString())));
		if (nearby.isEmpty()) return List.of();

		nearby.sort(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
		List<AbstractVillager> named = nearby.stream()
			.filter(v -> mentions(chatText, v.getCustomName().getString()))
			.toList();
		List<AbstractVillager> pool = named.isEmpty() ? nearby : named;
		return pool.subList(0, Math.min(pool.size(), Math.max(1, cfg.chat.max_responders)));
	}

	/** True if the message contains the villager's name, ignoring case and treating spaces/underscores alike. */
	public static boolean mentions(String message, String villagerName) {
		String name = AiConfig.normalizeName(villagerName);
		if (name.isEmpty() || message == null) return false;
		String msg = "_" + AiConfig.normalizeName(message.replaceAll("[^\\p{L}\\p{N}_\\s-]", " ")) + "_";
		return msg.contains("_" + name + "_");
	}

	/** Long replies stay up long enough to read: the configured minimum, or 2 s plus one second per 15 characters. */
	public static int displaySeconds(String text, AiConfig cfg) {
		return Math.max(cfg.chat.display_seconds, 2 + text.length() / 15);
	}

	public String randomFallback(AiConfig cfg) {
		List<String> lines = cfg.fallback_lines;
		if (lines == null || lines.isEmpty()) {
			return "Hmm... give me a moment.";
		}
		return lines.get(random.nextInt(lines.size()));
	}

	public void forget(UUID playerId) {
		cooldowns.remove(playerId);
	}
}
