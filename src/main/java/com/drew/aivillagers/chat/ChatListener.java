package com.drew.aivillagers.chat;

import com.drew.aivillagers.bubble.BubbleManager;
import com.drew.aivillagers.config.AiConfig;
import com.drew.aivillagers.ollama.OllamaClient;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChatListener {
	private static final Logger LOG = LoggerFactory.getLogger("aivillagers-chat");

	private final AiConfig config;
	private final OllamaClient ollama;
	private final BubbleManager bubbles;
	private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
	private final AtomicInteger pendingCount = new AtomicInteger(0);
	private final Random random = new Random();

	public ChatListener(AiConfig config, OllamaClient ollama, BubbleManager bubbles) {
		this.config = config;
		this.ollama = ollama;
		this.bubbles = bubbles;
	}

	public void onChatMessage(PlayerChatMessage message, ServerPlayer player, ChatType.Bound boundChatType) {
		if (player == null || message == null || player.level().isClientSide()) {
			return;
		}

		String chatText = message.decoratedContent() != null ? message.decoratedContent().getString() : message.signedContent();
		if (chatText == null || chatText.isBlank()) {
			return;
		}

		String lower = chatText.toLowerCase(Locale.ROOT);

		// Check player cooldown
		long now = System.currentTimeMillis();
		long cooldownMs = config.chat.cooldown_seconds * 1000L;
		Long lastTime = cooldowns.get(player.getUUID());
		if (lastTime != null && (now - lastTime) < cooldownMs) {
			return;
		}

		// Find nearby named villagers
		double r = config.chat.radius;
		AABB box = player.getBoundingBox().inflate(r);
		List<AbstractVillager> nearby = player.level().getEntitiesOfClass(AbstractVillager.class, box, v -> v.isAlive() && v.hasCustomName());

		AbstractVillager target = null;
		String villagerName = null;

		for (AbstractVillager v : nearby) {
			Component comp = v.getCustomName();
			if (comp == null) continue;
			String name = comp.getString().trim();
			if (!name.isEmpty() && lower.contains(name.toLowerCase(Locale.ROOT))) {
				target = v;
				villagerName = name;
				break;
			}
		}

		if (target == null) {
			return;
		}

		// Turn villager towards the player
		target.getLookControl().setLookAt(player, 30.0f, 30.0f);

		// Queue limit check
		if (pendingCount.get() >= config.chat.max_queue) {
			bubbles.show(target, randomFallback(), config.chat.display_seconds);
			return;
		}

		cooldowns.put(player.getUUID(), now);

		// Show thinking indicator
		final AbstractVillager villager = target;
		bubbles.show(villager, "...", 30);
		pendingCount.incrementAndGet();

		String persona = config.getPersona(villagerName);
		String userPrompt = player.getName().getString() + ": " + chatText.trim();

		ollama.chat(persona, userPrompt, config.ollama).whenComplete((reply, err) -> {
			pendingCount.decrementAndGet();
			String text;
			if (err != null || reply == null || reply.isBlank()) {
				text = randomFallback();
			} else {
				text = cleanAndTruncate(reply, config.chat.max_reply_chars);
			}

			player.level().getServer().execute(() -> {
				if (villager.isAlive() && !villager.isRemoved()) {
					bubbles.show(villager, text, config.chat.display_seconds);
				}
			});
		});
	}

	public String randomFallback() {
		List<String> lines = config.fallback_lines;
		if (lines == null || lines.isEmpty()) {
			return "Hmm... give me a moment.";
		}
		return lines.get(random.nextInt(lines.size()));
	}

	public static String cleanAndTruncate(String input, int maxChars) {
		if (input == null) return "";
		String cleaned = input.replace('\r', ' ').replace('\n', ' ').trim();

		// Remove leading/trailing quotes if the model wrapped the speech in quotes
		if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
			cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
		}

		if (cleaned.length() <= maxChars) {
			return cleaned;
		}
		return cleaned.substring(0, Math.max(1, maxChars - 1)).trim() + "…";
	}
}
