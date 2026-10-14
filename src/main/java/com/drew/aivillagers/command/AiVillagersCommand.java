package com.drew.aivillagers.command;

import com.drew.aivillagers.AiVillagers;
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
			.then(Commands.literal("test")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("name", StringArgumentType.word())
					.then(Commands.argument("message", StringArgumentType.greedyString())
						.executes(ctx -> {
							CommandSourceStack source = ctx.getSource();
							String name = StringArgumentType.getString(ctx, "name");
							String msg = StringArgumentType.getString(ctx, "message");
							AiConfig cfg = mod.getConfig();
							String persona = cfg.getPersona(name);

							source.sendSuccess(() -> Component.literal("§7[AI Villagers] Testing dialogue for §f" + name + "§7: §o\"" + msg + "\"§r"), false);

							mod.getOllamaClient().chat(persona, msg, cfg.ollama).whenComplete((reply, err) -> {
								source.getServer().execute(() -> {
									if (err != null || reply == null || reply.isBlank()) {
										source.sendFailure(Component.literal("[AI Villagers] Error querying model: " + (err != null ? err.getMessage() : "empty reply")));
									} else {
										source.sendSuccess(() -> Component.literal("§e" + name + "§7: §f" + reply), false);
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
