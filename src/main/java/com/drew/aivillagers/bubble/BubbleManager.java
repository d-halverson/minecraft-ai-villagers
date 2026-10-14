package com.drew.aivillagers.bubble;

import com.mojang.math.Transformation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BubbleManager {
	private static final Logger LOG = LoggerFactory.getLogger("aivillagers-bubble");
	public static final String TAG_BUBBLE = "aiv_bubble";

	private final Map<UUID, ActiveBubble> active = new ConcurrentHashMap<>();
	/** Displays spawned by this session. Tagged displays not in here are leftovers from a crash or chunk save. */
	private final Set<UUID> owned = ConcurrentHashMap.newKeySet();

	private static final class ActiveBubble {
		final Display.TextDisplay display;
		final AbstractVillager villager;
		int ticksRemaining;

		ActiveBubble(Display.TextDisplay display, AbstractVillager villager, int ticksRemaining) {
			this.display = display;
			this.villager = villager;
			this.ticksRemaining = ticksRemaining;
		}
	}

	public void show(AbstractVillager villager, String text, int seconds) {
		if (villager == null || !villager.isAlive() || !(villager.level() instanceof ServerLevel level)) {
			return;
		}

		UUID vid = villager.getUUID();
		ActiveBubble existing = active.get(vid);
		int ticks = Math.max(1, seconds * 20);

		if (existing != null && !existing.display.isRemoved() && existing.display.getVehicle() == villager) {
			// Update the existing text display to avoid flickering
			existing.display.setText(Component.literal(text));
			existing.ticksRemaining = ticks;
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
		display.setLineWidth(200);
		display.setTransformation(new Transformation(new Vector3f(0.0f, 0.9f, 0.0f), null, null, null));

		// Must be registered before addFreshEntity: spawning fires ENTITY_LOAD, which discards unowned bubbles
		owned.add(display.getUUID());
		if (!level.addFreshEntity(display) || !display.startRiding(villager, true, false)) {
			LOG.warn("Could not attach speech bubble to villager {}", vid);
			owned.remove(display.getUUID());
			display.discard();
			return;
		}

		active.put(vid, new ActiveBubble(display, villager, ticks));
	}

	/** Whether an entity being loaded is a speech bubble left over from an earlier session. */
	public boolean isOrphan(Entity entity) {
		return entity instanceof Display.TextDisplay && entity.entityTags().contains(TAG_BUBBLE) && !owned.contains(entity.getUUID());
	}

	/** The text currently shown above a villager, or null. */
	public String currentText(AbstractVillager villager) {
		ActiveBubble bubble = active.get(villager.getUUID());
		return bubble == null || bubble.display.isRemoved() ? null : bubble.display.getText().getString();
	}

	public void remove(AbstractVillager villager) {
		if (villager == null) return;
		removeById(villager.getUUID());
	}

	public void removeById(UUID villagerId) {
		ActiveBubble bubble = active.remove(villagerId);
		if (bubble != null) discard(bubble.display);
	}

	public void tick() {
		if (active.isEmpty()) return;

		active.entrySet().removeIf(entry -> {
			ActiveBubble bubble = entry.getValue();
			if (bubble.display.isRemoved()) {
				owned.remove(bubble.display.getUUID());
				return true;
			}
			if (bubble.display.getVehicle() != bubble.villager || !bubble.villager.isAlive()) {
				discard(bubble.display);
				return true;
			}
			if (--bubble.ticksRemaining <= 0) {
				discard(bubble.display);
				return true;
			}
			return false;
		});
	}

	public void clearAll() {
		for (ActiveBubble bubble : active.values()) {
			discard(bubble.display);
		}
		active.clear();
	}

	private void discard(Display.TextDisplay display) {
		owned.remove(display.getUUID());
		if (!display.isRemoved()) {
			display.stopRiding();
			display.discard();
		}
	}
}
