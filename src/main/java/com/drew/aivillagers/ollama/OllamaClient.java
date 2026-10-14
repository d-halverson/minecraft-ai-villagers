package com.drew.aivillagers.ollama;

import com.drew.aivillagers.config.AiConfig;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class OllamaClient {
	private static final Logger LOG = LoggerFactory.getLogger("aivillagers-ollama");
	private static final Gson GSON = new Gson();

	private final HttpClient http;

	public OllamaClient() {
		this.http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(4))
			.build();
	}

	public CompletableFuture<String> chat(String persona, String playerMessage, AiConfig.OllamaConfig cfg) {
		JsonObject options = new JsonObject();
		options.addProperty("num_predict", cfg.num_predict);
		options.addProperty("num_thread", cfg.num_thread);
		options.addProperty("temperature", cfg.temperature);

		JsonArray messages = new JsonArray();
		messages.add(message("system", persona));
		messages.add(message("user", playerMessage));

		JsonObject body = new JsonObject();
		body.addProperty("model", cfg.model);
		body.addProperty("stream", false);
		body.addProperty("think", false); // Disables reasoning thoughts for Qwen3 / DeepSeek to stay in token budget
		body.addProperty("keep_alive", "24h");
		body.add("options", options);
		body.add("messages", messages);

		String jsonString = GSON.toJson(body);

		HttpRequest req = HttpRequest.newBuilder(URI.create(cfg.url))
			.timeout(Duration.ofSeconds(cfg.timeout_seconds))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(jsonString))
			.build();

		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
			.thenApply(response -> {
				if (response.statusCode() != 200) {
					throw new IllegalStateException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
				}
				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
				if (!root.has("message")) {
					throw new IllegalStateException("Missing 'message' field in Ollama response: " + response.body());
				}
				JsonObject msg = root.getAsJsonObject("message");
				return msg.get("content").getAsString().trim();
			})
			.exceptionally(ex -> {
				LOG.warn("Ollama query failed: {}", ex.getMessage());
				return null;
			});
	}

	public CompletableFuture<Boolean> ping(AiConfig.OllamaConfig cfg) {
		try {
			URI base = URI.create(cfg.url);
			URI tagsUri = URI.create(base.getScheme() + "://" + base.getAuthority() + "/api/tags");
			HttpRequest req = HttpRequest.newBuilder(tagsUri)
				.timeout(Duration.ofSeconds(3))
				.GET()
				.build();
			return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
				.thenApply(r -> r.statusCode() == 200)
				.exceptionally(e -> false);
		} catch (Exception e) {
			return CompletableFuture.completedFuture(false);
		}
	}

	private static JsonObject message(String role, String content) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role);
		m.addProperty("content", content);
		return m;
	}
}
