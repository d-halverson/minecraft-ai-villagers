package com.drew.aivillagers.command;

import com.drew.aivillagers.AiVillagers;
import com.drew.aivillagers.chat.ReplyFormatter;
import com.drew.aivillagers.config.AiConfig;
import com.drew.aivillagers.ollama.OllamaClient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class AiVillagersCommand {
	private AiVillagersCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, AiVillagers mod) {
		var root = Commands.literal("aivillagers")
			.then(Commands.literal("reload")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(ctx -> {
					mod.reloadConfig();
					ctx.getSource().sendSuccess(() -> Component.literal("§a[AI Villagers] Config reloaded successfully."), true);
					return 1;
				}))
			.then(Commands.literal("status")
				.requires(Commands.hasPermission(Commands.LEVEL_ALL))
				.executes(ctx -> {
					CommandSourceStack source = ctx.getSource();
					AiConfig cfg = mod.getConfig();
					source.sendSuccess(() -> Component.literal("§6[AI Villagers] Status:"), false);
					source.sendSuccess(() -> Component.literal("§7- Model: §f" + cfg.ollama.model), false);
					source.sendSuccess(() -> Component.literal("§7- Ollama URL: §f" + cfg.ollama.url), false);
					source.sendSuccess(() -> Component.literal("§7- Chat radius: §f" + cfg.chat.radius + " blocks"), false);
					source.sendSuccess(() -> Component.literal("§7- Cooldown: §f" + cfg.chat.cooldown_seconds + "s"), false);
					source.sendSuccess(() -> Component.literal("§7- Max reply: §f" + cfg.chat.max_reply_chars + " chars / " + cfg.ollama.num_predict + " tokens"), false);
					source.sendSuccess(() -> Component.literal("§7- Stand still near players: §f" + (cfg.chat.freeze_near_players ? "on (" + cfg.chat.freeze_radius + " blocks)" : "off")), false);
					source.sendSuccess(() -> Component.literal("§7- AI villagers: §f" + (cfg.chat.require_persona ? String.join(", ", cfg.personas.keySet()) : "every named villager")), false);

					OllamaClient client = mod.getOllamaClient();
					client.ping(cfg.ollama).whenComplete((ok, err) -> {
						boolean reachable = ok != null && ok;
						source.getServer().execute(() -> {
							if (reachable) {
								source.sendSuccess(() -> Component.literal("§7- Connection: §aConnected to Ollama"), false);
							} else {
								source.sendSuccess(() -> Component.literal("§7- Connection: §cCannot reach Ollama at " + cfg.ollama.url), false);
							}
						});
					});
					return 1;
				}))
			.then(Commands.literal("persona")
				.requires(Commands.hasPermission(Commands.LEVEL_ALL))
				.then(Commands.argument("name", StringArgumentType.string())
					.executes(ctx -> {
						CommandSourceStack source = ctx.getSource();
						String name = StringArgumentType.getString(ctx, "name");
						AiConfig cfg = mod.getConfig();
						boolean custom = cfg.hasCustomPersona(name);
						String persona = cfg.getPersona(name);
						if (custom) {
							source.sendSuccess(() -> Component.literal("§6[AI Villagers] Custom persona for §f" + name + "§6: §7" + persona), false);
						} else {
							source.sendSuccess(() -> Component.literal("§7[AI Villagers] " + name + " uses default persona: §o\"" + persona + "\"§r"), false);
						}
						return 1;
					})
					.then(Commands.argument("description", StringArgumentType.greedyString())
						.executes(ctx -> {
							CommandSourceStack source = ctx.getSource();
							String name = StringArgumentType.getString(ctx, "name");
							String desc = StringArgumentType.getString(ctx, "description");
							mod.setVillagerPersona(name, desc);
							source.sendSuccess(() -> Component.literal("§a[AI Villagers] Set persona for §f" + name + "§a: §f" + desc), true);

							// If executed by an in-game player, notify any nearby matching villager within 16 blocks
							if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
								net.minecraft.world.phys.AABB box = sp.getBoundingBox().inflate(16.0);
								java.util.List<net.minecraft.world.entity.npc.villager.AbstractVillager> villagers = sp.level().getEntitiesOfClass(
									net.minecraft.world.entity.npc.villager.AbstractVillager.class, box,
									v -> v.isAlive() && v.hasCustomName() && AiConfig.normalizeName(name).equals(AiConfig.normalizeName(v.getCustomName().getString())));
								for (net.minecraft.world.entity.npc.villager.AbstractVillager v : villagers) {
									v.getLookControl().setLookAt(sp, 30.0f, 30.0f);
									mod.getBubbleManager().show(v, "*Nods* Understood.", 4);
								}
							}
							return 1;
						}))))
			.then(Commands.literal("resetpersona")
				.requires(Commands.hasPermission(Commands.LEVEL_ALL))
				.then(Commands.argument("name", StringArgumentType.string())
					.executes(ctx -> {
						CommandSourceStack source = ctx.getSource();
						String name = StringArgumentType.getString(ctx, "name");
						boolean removed = mod.removeVillagerPersona(name);
						if (removed) {
							source.sendSuccess(() -> Component.literal("§a[AI Villagers] Reset persona for §f" + name + "§a to default."), true);
						} else {
							source.sendFailure(Component.literal("[AI Villagers] No custom persona found for " + name + "."));
						}
						return 1;
					})))
			.then(Commands.literal("test")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("name", StringArgumentType.string())
					.then(Commands.argument("message", StringArgumentType.greedyString())
						.executes(ctx -> {
							CommandSourceStack source = ctx.getSource();
							String name = StringArgumentType.getString(ctx, "name");
							String msg = StringArgumentType.getString(ctx, "message");
							AiConfig cfg = mod.getConfig();
							String prompt = cfg.getSystemPrompt(name);
							String speaker = source.getEntity() != null ? source.getEntity().getName().getString() : "Server";

							source.sendSuccess(() -> Component.literal("§7[AI Villagers] Testing dialogue for §f" + name + "§7: §o\"" + msg + "\"§r"), false);

							mod.getOllamaClient().chat(prompt, cfg.getUserPrompt(name, speaker, msg), cfg.ollama).whenComplete((reply, err) -> {
								String text = err == null && reply != null ? ReplyFormatter.fit(reply.text(), cfg.chat.max_reply_chars, reply.cutOff(), name) : "";
								source.getServer().execute(() -> {
									if (text.isBlank()) {
										source.sendFailure(Component.literal("[AI Villagers] Error querying model: " + (err != null ? err.getMessage() : "no reply (is Ollama running?)")));
									} else {
										source.sendSuccess(() -> Component.literal("§e" + name + "§7: §f" + text), false);
									}
								});
							});
							return 1;
						}))));

		dispatcher.register(root);
		// Also register shorthand alias /llmv
		dispatcher.register(Commands.literal("llmv")
			.requires(Commands.hasPermission(Commands.LEVEL_ALL))
			.redirect(dispatcher.getRoot().getChild("aivillagers")));
	}
}
