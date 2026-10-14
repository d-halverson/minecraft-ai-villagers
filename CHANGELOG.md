# Changelog

## 0.2.0

* **Talk without saying a name.** Any chat message makes nearby AI villagers (named villagers with a persona) answer, within 16 blocks. Naming a villager in the message picks who answers; otherwise the nearest ones answer (up to `chat.max_responders`, default 2).
* **AI villagers stand still while a player is within 16 blocks** and look at the nearest player, so you don't have to chase them while typing. They still flee when attacked or when a monster is near. Uses a temporary speed modifier that is never saved with the villager. Turn off with `chat.freeze_near_players`.
* **No more cut-off replies.** Replies can be longer (`max_reply_chars` 60 → 150, `num_predict` 20 → 64). The length limit is now given to the model with each message, so it actually follows it, and a reply that still runs over is trimmed to whole sentences instead of being chopped mid-word. Long replies stay on screen longer.
* Names with spaces work: a villager tagged `Chris Hansen` uses the persona saved as `chris_hansen` (and vice versa). Quote names with spaces in commands: `/aivillagers persona "Chris Hansen" ...`.
* Only villagers with a persona talk (`chat.require_persona`, default true), so ordinary named villagers stay quiet.
* Fixed: the speech-bubble cleanup could delete a new bubble the moment it spawned; it now only removes bubbles left over from an earlier session.
* Fixed: `/aivillagers test` now uses the same prompt and length limit as in-game chat.
* Fixed: a broken `aivillagers.json` is no longer overwritten with defaults (which wiped personas). Saves are atomic.
* Existing configs are upgraded automatically: values still at the 0.1.0 defaults move to the new ones, values you changed are kept.
* The self-test (`./gradlew runSelftest`) now runs real chat, freezing and bubble checks over several ticks.

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
