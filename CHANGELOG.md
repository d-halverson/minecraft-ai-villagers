# Changelog

## 0.1.0

* Initial release of **AI Villagers** for Minecraft 26.3 (Fabric).
* Mention any named villager in chat within range (default 8 blocks) to start a conversation.
* The villager turns towards you and displays a floating speech bubble above its head using vanilla `TextDisplay` entities.
* Powered by a local Ollama LLM (`qwen2.5:0.5b` default) running on CPU with minimal latency (~1-2 seconds per reply).
* 100% server-side: vanilla unmodded clients can connect and see the dialogue bubbles with zero client mods or resource packs.
* Per-villager customizable personas in `config/aivillagers.json` (e.g. `gareth: "You are Gareth, a grumpy blacksmith villager..."`).
* In-game persona creation:
  * **Book & Quill / Written Book:** Players can write any backstory or personality in a book, then sneak-right-click a named villager to permanently teach it its persona.
  * **In-game commands:** `/aivillagers persona <name> <text>` to assign, `/aivillagers persona <name>` to view, and `/aivillagers resetpersona <name>` to reset.
* Automatic cleanup of orphaned speech bubbles on startup, server shutdown, or villager despawn/death.
* Admin commands: `/aivillagers reload` (or `/llmv reload`), `/aivillagers status`, and `/aivillagers test <name> <message>`.
* Automated in-game self-tests via `-Daivillagers.selftest=1`.
