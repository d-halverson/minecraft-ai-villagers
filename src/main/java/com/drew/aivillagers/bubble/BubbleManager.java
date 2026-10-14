package com.drew.aivillagers.bubble;

import com.mojang.math.Transformation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BubbleManager {
	private static final Logger LOG = LoggerFactory.getLogger("aivillagers-bubble");
	public static final String TAG_BUBBLE = "aiv_bubble";

	private final Map<UUID, ActiveBubble> active = new ConcurrentHashMap<>();

	private static final class ActiveBubble {
		final UUID villagerId;
		final Display.TextDisplay display;
		int ticksRemaining;

		ActiveBubble(UUID villagerId, Display.TextDisplay display, int ticksRemaining) {
			this.villagerId = villagerId;
			this.display = display;
			this.ticksRemaining = ticksRemaining;
		}
	}

	public void show(AbstractVillager villager, String text, int seconds) {
		if (villager == null || !villager.isAlive() || !(villager.level() instanceof ServerLevel level)) {
			return;
		}

		UUID vid = villager.getUUID();
		ActiveBubble existing = active.get(vid);

		if (existing != null && existing.display.isAlive() && !existing.display.isRemoved()) {
			// Update the existing text display to avoid flickering
			existing.display.setText(Component.literal(text));
			existing.ticksRemaining = Math.max(1, seconds * 20);
			return;
		}

		// Remove any stale bubble before creating a new one
		remove(villager);

		Display.TextDisplay display = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.EVENT);
		if (display == null) {
			LOG.warn("Failed to create TextDisplay entity for villager {}", vid);
			return;
		}

		display.addTag(TAG_BUBBLE);
		display.setPos(villager.getX(), villager.getY(), villager.getZ());
		display.setText(Component.literal(text));
		display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		display.setBackgroundColor(0xA0000000); // 160 alpha black
		display.setLineWidth(140);
		display.setTransformation(new Transformation(new Vector3f(0.0f, 0.9f, 0.0f), null, null, null));

		level.addFreshEntity(display);
		display.startRiding(villager, true, false);

		active.put(vid, new ActiveBubble(vid, display, Math.max(1, seconds * 20)));
	}

	public void remove(AbstractVillager villager) {
		if (villager == null) return;
		removeById(villager.getUUID());
	}

	public void removeById(UUID villagerId) {
		ActiveBubble bubble = active.remove(villagerId);
		if (bubble != null && bubble.display != null && !bubble.display.isRemoved()) {
			bubble.display.stopRiding();
			bubble.display.discard();
		}
	}

	public void tick() {
		if (active.isEmpty()) return;

		active.entrySet().removeIf(entry -> {
			ActiveBubble bubble = entry.getValue();
			if (bubble.display == null || bubble.display.isRemoved() || !bubble.display.isAlive()) {
				return true;
			}
			if (bubble.display.getVehicle() == null || !bubble.display.getVehicle().isAlive()) {
				bubble.display.stopRiding();
				bubble.display.discard();
				return true;
			}
			bubble.ticksRemaining--;
			if (bubble.ticksRemaining <= 0) {
				bubble.display.stopRiding();
				bubble.display.discard();
				return true;
			}
			return false;
		});
	}

	public void clearAll() {
		for (ActiveBubble bubble : active.values()) {
			if (bubble.display != null && !bubble.display.isRemoved()) {
				bubble.display.stopRiding();
				bubble.display.discard();
			}
		}
		active.clear();
	}
}
