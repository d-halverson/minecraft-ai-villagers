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

		// Register commands
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			AiVillagersCommand.register(dispatcher, this);
		});

		// Dev self-test hook
		SelfTest.init(this);

		LOG.info("AI Villagers initialized (model: {}, url: {})", config.ollama.model, config.ollama.url);
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
