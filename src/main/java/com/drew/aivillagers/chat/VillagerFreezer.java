package com.drew.aivillagers.chat;

import com.drew.aivillagers.AiVillagers;
import com.drew.aivillagers.config.AiConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

import java.util.*;
import java.util.function.Supplier;

/**
 * Makes AI villagers stand still (and look at the nearest player) while a player is within {@code freeze_radius},
 * so players do not have to chase them while typing. Uses a transient movement-speed modifier, which is never
 * saved with the villager, so a crash or restart cannot leave villagers stuck.
 */
public final class VillagerFreezer {
	public static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(AiVillagers.MOD_ID, "talk_freeze");
	private static final AttributeModifier FREEZE = new AttributeModifier(MODIFIER_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final int SCAN_INTERVAL_TICKS = 5;

	private final Supplier<AiConfig> config;
	private Map<UUID, AbstractVillager> frozen = new HashMap<>();
	private int ticks;

	public VillagerFreezer(Supplier<AiConfig> config) {
		this.config = config;
	}

	public void tick(MinecraftServer server) {
		if (++ticks % SCAN_INTERVAL_TICKS != 0) return;
		update(server.getPlayerList().getPlayers());
	}

	/** Freezes AI villagers near any of {@code players} and releases the rest. Server thread only. */
	public void update(Collection<ServerPlayer> players) {
		AiConfig cfg = config.get();
		Map<UUID, AbstractVillager> near = new HashMap<>();
		Map<UUID, ServerPlayer> closest = new HashMap<>();

		if (cfg.chat.freeze_near_players) {
			double r = cfg.chat.freeze_radius;
			double r2 = r * r;
			for (ServerPlayer player : players) {
				if (!player.isAlive() || player.isSpectator()) continue;
				List<AbstractVillager> villagers = player.level().getEntitiesOfClass(AbstractVillager.class,
					player.getBoundingBox().inflate(r),
					v -> v.isAlive() && v.hasCustomName() && v.distanceToSqr(player) <= r2
						&& cfg.isAiVillagerName(v.getCustomName().getString()));
				for (AbstractVillager v : villagers) {
					ServerPlayer prev = closest.get(v.getUUID());
					if (prev == null || v.distanceToSqr(player) < v.distanceToSqr(prev)) {
						closest.put(v.getUUID(), player);
						near.put(v.getUUID(), v);
					}
				}
			}
		}

		for (AbstractVillager v : near.values()) {
			if (inDanger(v)) {
				release(v);
				continue;
			}
			hold(v, closest.get(v.getUUID()));
		}
		for (Map.Entry<UUID, AbstractVillager> e : frozen.entrySet()) {
			if (!near.containsKey(e.getKey())) release(e.getValue());
		}
		near.values().removeIf(VillagerFreezer::inDanger);
		frozen = near;
	}

	public boolean isFrozen(AbstractVillager villager) {
		AttributeInstance speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
		return speed != null && speed.hasModifier(MODIFIER_ID);
	}

	public void releaseAll() {
		frozen.values().forEach(VillagerFreezer::release);
		frozen = new HashMap<>();
	}

	/** Never pin a villager in place while it is being attacked or a monster is near; let it flee. */
	private static boolean inDanger(AbstractVillager v) {
		var brain = v.getBrain();
		return brain.hasMemoryValue(MemoryModuleType.HURT_BY) || brain.hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
	}

	private static void hold(AbstractVillager v, ServerPlayer player) {
		AttributeInstance speed = v.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(MODIFIER_ID)) {
			speed.addTransientModifier(FREEZE);
		}
		v.getNavigation().stop();
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		if (player != null && !v.isSleeping()) {
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
			v.getLookControl().setLookAt(player, 30.0f, 30.0f);
		}
	}

	private static void release(AbstractVillager v) {
		AttributeInstance speed = v.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(MODIFIER_ID);
	}
}
