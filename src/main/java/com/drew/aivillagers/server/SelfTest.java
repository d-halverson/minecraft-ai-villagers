package com.drew.aivillagers.server;

import com.drew.aivillagers.AiVillagers;
import com.drew.aivillagers.bubble.BubbleManager;
import com.drew.aivillagers.chat.ReplyFormatter;
import com.drew.aivillagers.config.AiConfig;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Dev-only checks against a real server, logged with a [SELFTEST] prefix. Runs over several ticks so that
 * entity loading, villager AI and the async Ollama reply all happen for real.
 */
public final class SelfTest {
	private static final Logger LOG = LoggerFactory.getLogger("aivillagers-selftest");
	private static final int REPLY_TIMEOUT_TICKS = 40 * 20;

	private SelfTest() {}

	public static void init(AiVillagers mod) {
		if (System.getProperty("aivillagers.selftest") == null) return;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> new Run(mod, server).start());
	}

	private static final class Run {
		final AiVillagers mod;
		final MinecraftServer srv;
		boolean allPassed = true;
		boolean ollamaUp;
		ServerLevel level;
		Villager chris;
		Villager bob;
		FakePlayer player;
		Vec3 frozenAt;
		Vec3 bobStart;
		int tick;
		int replyStart = -1;
		boolean done;

		Run(AiVillagers mod, MinecraftServer srv) {
			this.mod = mod;
			this.srv = srv;
		}

		void check(String name, boolean ok, String detail) {
			LOG.info("[SELFTEST] {}: {} => {}", name, detail, ok ? "PASS" : "FAIL");
			allPassed &= ok;
		}

		void start() {
			LOG.info("[SELFTEST] Running AI Villagers automated self-tests...");
			AiConfig cfg = mod.getConfig();
			cfg.personas.put("chris_hansen", "You are Chris Hansen, a TV host who asks pointed questions.");

			String prompt = cfg.getSystemPrompt("Chris Hansen");
			check("personaResolution", prompt.startsWith("You are Chris Hansen") && prompt.contains(cfg.maxReplyWords() + " words")
				&& cfg.isAiVillagerName("Chris Hansen") && !cfg.isAiVillagerName("Bob"), "prompt='" + prompt.replace('\n', ' ') + "'");

			String fitted = ReplyFormatter.fit("The forge is hot. I have three swords to finish before the", 150, true, "Gareth");
			check("replyFormatter", fitted.equals("The forge is hot."), "output='" + fitted + "'");

			level = srv.overworld();
			BlockPos spawn = level.getRespawnData().pos();
			level.setChunkForced(spawn.getX() >> 4, spawn.getZ() >> 4, true);
			BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, spawn);
			chris = spawnVillager("Chris Hansen", ground);
			bob = spawnVillager("Bob", ground.east(2));
			player = FakePlayer.get(level);
			player.setPos(ground.getX() + 3.5, ground.getY(), ground.getZ() + 0.5);

			// Bubble on Bob (no persona, so chat will not touch it) must survive the entity-load cleanup
			mod.getBubbleManager().show(bob, "Hello selftest!", 30);

			try {
				ollamaUp = Boolean.TRUE.equals(mod.getOllamaClient().ping(cfg.ollama).join());
			} catch (Exception e) {
				ollamaUp = false;
			}

			ServerTickEvents.END_SERVER_TICK.register(s -> {
				if (done) return;
				try {
					onTick();
				} catch (Throwable t) {
					LOG.error("[SELFTEST] Exception during self-test", t);
					allPassed = false;
					finish();
				}
			});
		}

		Villager spawnVillager(String name, BlockPos pos) {
			Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			v.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			v.setCustomName(Component.literal(name));
			level.addFreshEntity(v);
			return v;
		}

		void walk(Villager v) {
			BlockPos target = BlockPos.containing(v.position()).south(10);
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.6f, 0));
		}

		void onTick() {
			tick++;
			// The real freezer only sees connected players; keep applying it for the fake one
			mod.getFreezer().update(List.of(player));
			BubbleManager bubbles = mod.getBubbleManager();

			if (tick == 10) {
				// Freeze: only the persona villager, only while the player is near
				boolean chrisFrozen = mod.getFreezer().isFrozen(chris);
				boolean bobFrozen = mod.getFreezer().isFrozen(bob);
				Vec3 near = player.position();
				player.setPos(near.x + 40, near.y, near.z);
				mod.getFreezer().update(List.of(player));
				boolean releasedWhenFar = !mod.getFreezer().isFrozen(chris);
				player.setPos(near.x, near.y, near.z);
				mod.getFreezer().update(List.of(player));
				check("freezeNearPlayer", chrisFrozen && !bobFrozen && releasedWhenFar,
					"chrisFrozen=" + chrisFrozen + " bobFrozen=" + bobFrozen + " releasedWhenFar=" + releasedWhenFar);
			}
			if (tick == 20) {
				// Send both villagers walking: only the AI villager near the player should stay put
				frozenAt = chris.position();
				bobStart = bob.position();
				walk(chris);
				walk(bob);
			}
			if (tick > 20 && tick < 120 && tick % 20 == 0) {
				walk(chris);
				walk(bob);
			}
			if (tick == 60) {
				boolean riding = !bob.getPassengers().isEmpty() && bob.getPassengers().getFirst() instanceof Display.TextDisplay;
				String text = bubbles.currentText(bob);
				check("bubbleSurvivesTicks", riding && "Hello selftest!".equals(text), "riding=" + riding + " text='" + text + "'");
				bubbles.remove(bob);
				check("bubbleRemoved", bob.getPassengers().isEmpty(), "passengers=" + bob.getPassengers().size());
			}
			if (tick == 120) {
				double moved = chris.position().subtract(frozenAt).horizontalDistance();
				double bobMoved = bob.position().subtract(bobStart).horizontalDistance();
				check("frozenVillagerStaysPut", moved < 0.3 && bobMoved > 1.0 && mod.getFreezer().isFrozen(chris),
					String.format("AI villager moved %.2f blocks, plain villager (control) moved %.2f blocks in 5s", moved, bobMoved));

				// Chat without any name: the persona villager answers, the plain named one does not
				List<AbstractVillager> responders = mod.getChatListener().handle(player, "hello there, what do you do all day?");
				boolean onlyChris = responders.size() == 1 && responders.getFirst() == chris;
				check("chatWithoutNameReachesPersonaVillager", onlyChris, "responders=" + responders.stream().map(v -> v.getCustomName().getString()).toList());
				replyStart = tick;
			}
			if (replyStart > 0) {
				String text = bubbles.currentText(chris);
				boolean answered = text != null && !text.equals("...");
				if (answered || tick - replyStart > REPLY_TIMEOUT_TICKS) {
					int max = mod.getConfig().chat.max_reply_chars;
					boolean isFallback = text != null && mod.getConfig().fallback_lines.contains(text);
					if (ollamaUp) {
						check("villagerReply", answered && !isFallback && text.length() <= max,
							"after " + (tick - replyStart) / 20.0 + "s text='" + text + "' len=" + (text == null ? 0 : text.length()) + " max=" + max);
					} else {
						LOG.warn("[SELFTEST] villagerReply: Ollama is not running at {} => SKIP (bubble showed '{}')", mod.getConfig().ollama.url, text);
					}
					finish();
				}
			}
		}

		void finish() {
			done = true;
			chris.discard();
			bob.discard();
			if (allPassed) {
				LOG.info("[SELFTEST] ========================================");
				LOG.info("[SELFTEST] ALL CHECKS PASSED!");
				LOG.info("[SELFTEST] ========================================");
			} else {
				LOG.error("[SELFTEST] SOME CHECKS FAILED!");
			}
			if ("1".equals(System.getProperty("aivillagers.selftest.exit"))) {
				LOG.info("[SELFTEST] Exiting server as requested by aivillagers.selftest.exit=1");
				srv.halt(false);
			}
		}
	}
}
