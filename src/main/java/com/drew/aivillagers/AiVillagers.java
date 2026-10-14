package com.drew.aivillagers;

import com.drew.aivillagers.bubble.BubbleManager;
import com.drew.aivillagers.chat.ChatListener;
import com.drew.aivillagers.command.AiVillagersCommand;
import com.drew.aivillagers.config.AiConfig;
import com.drew.aivillagers.ollama.OllamaClient;
import com.drew.aivillagers.server.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Display;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class AiVillagers implements ModInitializer {
	public static final String MOD_ID = "aivillagers";
	private static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	private static AiVillagers instance;

	private Path configDir;
	private AiConfig config;
	private OllamaClient ollamaClient;
	private BubbleManager bubbleManager;
	private ChatListener chatListener;

	@Override
	public void onInitialize() {
		instance = this;
		configDir = FabricLoader.getInstance().getConfigDir();
		config = AiConfig.load(configDir);
		ollamaClient = new OllamaClient();
		bubbleManager = new BubbleManager();
		chatListener = new ChatListener(config, ollamaClient, bubbleManager);

		// Register chat listener
		ServerMessageEvents.CHAT_MESSAGE.register((message, player, bound) -> {
			chatListener.onChatMessage(message, player, bound);
		});

		// Tick bubble timers
		ServerTickEvents.END_SERVER_TICK.register(server -> bubbleManager.tick());

		// Clean up bubbles on server stopping
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> bubbleManager.clearAll());

		// Prevent orphaned text displays from old sessions
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof Display.TextDisplay && entity.entityTags().contains(BubbleManager.TAG_BUBBLE)) {
				entity.discard();
			}
		});

		// Player item interaction: sneak-right-click a named villager with a Book & Quill or Written Book to give it a persona
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() || !(player instanceof net.minecraft.server.level.ServerPlayer sp) || hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			if (!(entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager villager)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			if (!sp.isShiftKeyDown()) {
				return net.minecraft.world.InteractionResult.PASS;
			}

			net.minecraft.world.item.ItemStack held = sp.getItemInHand(hand);
			if (!held.is(net.minecraft.world.item.Items.WRITABLE_BOOK) && !held.is(net.minecraft.world.item.Items.WRITTEN_BOOK)) {
				return net.minecraft.world.InteractionResult.PASS;
			}

			if (!villager.hasCustomName() || villager.getCustomName() == null) {
				sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[AI Villagers] Name this villager with a Name Tag before giving it a persona!"));
				return net.minecraft.world.InteractionResult.SUCCESS;
			}

			String name = villager.getCustomName().getString().trim();
			String text = extractBookText(held);
			if (text == null || text.isBlank()) {
				sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[AI Villagers] Write a personality in the book first!"));
				return net.minecraft.world.InteractionResult.SUCCESS;
			}

			setVillagerPersona(name, text);
			villager.getLookControl().setLookAt(sp, 30.0f, 30.0f);
			bubbleManager.show(villager, "*Reads book* Understood. I know who I am now.", 5);
			sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[AI Villagers] Set persona for §f" + name + "§a: §7" + text));
			return net.minecraft.world.InteractionResult.SUCCESS;
		});

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			AiVillagersCommand.register(dispatcher, this);
		});

		// Dev self-test hook
		SelfTest.init(this);

		LOG.info("AI Villagers initialized (model: {}, url: {})", config.ollama.model, config.ollama.url);
	}

	public static String extractBookText(net.minecraft.world.item.ItemStack stack) {
		if (stack.is(net.minecraft.world.item.Items.WRITABLE_BOOK)) {
			net.minecraft.world.item.component.WritableBookContent content = stack.get(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT);
			if (content != null) {
				StringBuilder sb = new StringBuilder();
				content.getPages(false).forEach(page -> {
					if (page != null && !page.isBlank()) {
						if (!sb.isEmpty()) sb.append(" ");
						sb.append(page.trim());
					}
				});
				return sb.toString().trim();
			}
		} else if (stack.is(net.minecraft.world.item.Items.WRITTEN_BOOK)) {
			net.minecraft.world.item.component.WrittenBookContent content = stack.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT);
			if (content != null) {
				StringBuilder sb = new StringBuilder();
				for (net.minecraft.network.chat.Component page : content.getPages(false)) {
					if (page != null) {
						String str = page.getString().trim();
						if (!str.isBlank()) {
							if (!sb.isEmpty()) sb.append(" ");
							sb.append(str);
						}
					}
				}
				return sb.toString().trim();
			}
		}
		return null;
	}

	public void setVillagerPersona(String villagerName, String personaText) {
		config.setPersona(villagerName, personaText, configDir);
		LOG.info("Set persona for villager {}: {}", villagerName, personaText);
	}

	public boolean removeVillagerPersona(String villagerName) {
		boolean removed = config.removePersona(villagerName, configDir);
		if (removed) {
			LOG.info("Removed persona for villager {}", villagerName);
		}
		return removed;
	}

	public void reloadConfig() {
		config = AiConfig.load(configDir);
		chatListener = new ChatListener(config, ollamaClient, bubbleManager);
		LOG.info("AI Villagers configuration reloaded.");
	}

	public static AiVillagers getInstance() {
		return instance;
	}

	public AiConfig getConfig() {
		return config;
	}

	public OllamaClient getOllamaClient() {
		return ollamaClient;
	}

	public BubbleManager getBubbleManager() {
		return bubbleManager;
	}

	public ChatListener getChatListener() {
		return chatListener;
	}
}
