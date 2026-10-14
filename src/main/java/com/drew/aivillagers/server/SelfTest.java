package com.drew.aivillagers.server;

import com.drew.aivillagers.AiVillagers;
import com.drew.aivillagers.bubble.BubbleManager;
import com.drew.aivillagers.chat.ChatListener;
import com.drew.aivillagers.config.AiConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SelfTest {
	private static final Logger LOG = LoggerFactory.getLogger("aivillagers-selftest");

	private SelfTest() {}

	public static void init(AiVillagers mod) {
		if (System.getProperty("aivillagers.selftest") == null) return;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> run(server, mod));
	}

	private static void run(MinecraftServer srv, AiVillagers mod) {
		LOG.info("[SELFTEST] Running AI Villagers automated self-tests...");
		boolean allPassed = true;

		try {
			// 1. Config & persona resolution
			AiConfig cfg = mod.getConfig();
			String garethPersona = cfg.getPersona("Gareth");
			boolean personaGarethOk = garethPersona != null && garethPersona.toLowerCase().contains("grumpy blacksmith");
			String bobPersona = cfg.getPersona("Bob");
			boolean personaBobOk = bobPersona != null && bobPersona.contains("Bob");
			boolean configOk = personaGarethOk && personaBobOk;
			LOG.info("[SELFTEST] personaResolution: gareth={} bob={} => {}", personaGarethOk, personaBobOk, configOk ? "PASS" : "FAIL");
			allPassed &= configOk;

			// 2. Text clean and truncate
			String raw = "\"Hello\nthere\rworld! This is a long sentence that definitely exceeds sixty characters in total length.\"";
			String cleaned = ChatListener.cleanAndTruncate(raw, 30);
			boolean truncateOk = !cleaned.contains("\n") && !cleaned.contains("\r") && !cleaned.startsWith("\"") && cleaned.length() <= 30 && cleaned.endsWith("…");
			LOG.info("[SELFTEST] cleanAndTruncate: output='{}' (len={}) => {}", cleaned, cleaned.length(), truncateOk ? "PASS" : "FAIL");
			allPassed &= truncateOk;

			// 3. In-world entity bubble spawning & mounting
			ServerLevel level = srv.overworld();
			BlockPos spawnPos = level.getRespawnData().pos();
			Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (villager != null) {
				villager.setPos(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5);
				villager.setCustomName(Component.literal("Gareth"));
				level.addFreshEntity(villager);

				BubbleManager bm = mod.getBubbleManager();
				bm.show(villager, "Hello selftest!", 10);

				boolean hasPassenger = !villager.getPassengers().isEmpty();
				boolean isTextDisplay = hasPassenger && (villager.getPassengers().getFirst() instanceof Display.TextDisplay);
				Display.TextDisplay td = isTextDisplay ? (Display.TextDisplay) villager.getPassengers().getFirst() : null;
				boolean textMatches = td != null && "Hello selftest!".equals(td.getText().getString());

				bm.remove(villager);
				boolean removedPassenger = villager.getPassengers().isEmpty();
				villager.discard();

				boolean bubbleOk = hasPassenger && isTextDisplay && textMatches && removedPassenger;
				LOG.info("[SELFTEST] bubbleMounting: hasPassenger={} isTextDisplay={} textMatches={} removedPassenger={} => {}",
					hasPassenger, isTextDisplay, textMatches, removedPassenger, bubbleOk ? "PASS" : "FAIL");
				allPassed &= bubbleOk;
			} else {
				LOG.error("[SELFTEST] bubbleMounting: Failed to create Villager entity => FAIL");
				allPassed = false;
			}

			// 4. Ollama connectivity & query check (if Ollama is running)
			try {
				Boolean ping = mod.getOllamaClient().ping(cfg.ollama).join();
				if (Boolean.TRUE.equals(ping)) {
					String reply = mod.getOllamaClient().chat(cfg.getPersona("Gareth"), "Hello Gareth", cfg.ollama).join();
					boolean ollamaOk = reply != null && !reply.isBlank();
					LOG.info("[SELFTEST] ollamaInference: reply='{}' => {}", reply, ollamaOk ? "PASS" : "FAIL");
					allPassed &= ollamaOk;
				} else {
					LOG.warn("[SELFTEST] ollamaInference: Ollama is not running at {} (optional for offline build) => SKIP", cfg.ollama.url);
				}
			} catch (Exception e) {
				LOG.warn("[SELFTEST] ollamaInference: Ollama check threw exception => SKIP", e);
			}

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
		} catch (Throwable t) {
			LOG.error("[SELFTEST] Exception during self-test", t);
		}
	}
}
