# AI Villagers

Talk to villagers in Minecraft with local LLMs (Ollama) and floating speech bubbles.  
A Fabric mod for **Minecraft 26.3**, powered by tiny, fast CPU-friendly models like `qwen2.5:0.5b`.

Vanilla clients can connect and see the floating dialogue bubbles with **zero client mods or resource packs**.

## What it is

* **Floating speech bubbles.** Name a villager with a name tag, mention its name in chat within range (default 8 blocks), and the villager turns to look at you and speaks with a floating text box above its head.
* **100% vanilla client compatible.** The speech bubble is rendered using Minecraft's built-in `TextDisplay` entities riding the villager. Unmodded vanilla players on your server can see and read the dialogue without installing any mod or pack.
* **Fast and 100% local (CPU inference).** Powered by [Ollama](https://ollama.com/). Runs on your server's CPU with negligible resource usage (~400 MB RAM for `qwen2.5:0.5b`). Replies take ~1–2 seconds.
* **Custom personas.** Every villager can have its own personality (e.g. Gareth the grumpy blacksmith, a superstitious cleric, or an excited fisherman).
* **Safe and hardened.** Built-in queue management, player cooldowns, length truncation, and automatic cleanup of speech bubbles on shutdown, death, or server restart (no orphaned displays).
* **Easy to host.** Place one jar (plus Fabric API) on your Fabric server.

Works on Windows, macOS, and Linux.

## Which file do I need?

Everything is on the [Releases](../../releases) page.

| Release file | What it is | Who needs it |
| --- | --- | --- |
| `aivillagers-x.y.z.jar` | The mod jar | **The server** (and singleplayer worlds). Together with [Fabric API](https://modrinth.com/mod/fabric-api) |
| `SHA256SUMS.txt` | Checksums of release files | Optional: to verify your download |

Players joining a multiplayer server **do not need to install the mod**. Only the server needs it!

## Ollama setup (local AI)

The mod communicates with a local [Ollama](https://ollama.com/) instance over its standard HTTP API (`http://localhost:11434`).

1. **Install Ollama**
   * **macOS / Linux:** `brew install ollama` or download from [ollama.com](https://ollama.com)
   * **Windows:** Download the installer from [ollama.com](https://ollama.com/download)

2. **Pull the default model:**
   ```bash
   ollama pull qwen2.5:0.5b
   ```
   * *Smallest & fastest (default):* `qwen2.5:0.5b` (~400 MB) — follows character instructions well and replies in ~1 second.
   * *Ultra-light:* `gemma3:270m` (~290 MB) — very fast on low-spec hardware.
   * *Higher quality dialogue:* `llama3.2:1b` (~1.3 GB) — richer vocabulary and depth.

3. **Start Ollama with CPU-friendly settings:**
   ```bash
   export OLLAMA_NUM_PARALLEL=1
   export OLLAMA_KEEP_ALIVE=24h
   ollama serve
   ```
   (On Windows PowerShell, use `$env:OLLAMA_NUM_PARALLEL="1"`, `$env:OLLAMA_KEEP_ALIVE="24h"`, `ollama serve`).

## Server install

Requires Java 25 and a [Fabric](https://fabricmc.net/use/server/) server for Minecraft 26.3 (loader 0.19.5 or newer).

1. Set up a Fabric server for 26.3 and start it once.
2. Put these two jars into the server's `mods/` folder:
   * [Fabric API](https://modrinth.com/mod/fabric-api) `0.162.0+26.3` (or newer for 26.3)
   * `aivillagers-<version>.jar`
3. Ensure Ollama is running (`ollama serve`).
4. Start the server. Configuration is generated automatically at `config/aivillagers.json`.

## Singleplayer install

If you play in singleplayer:

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.3.
2. Place `fabric-api-0.162.0+26.3.jar` and `aivillagers-<version>.jar` in your `.minecraft/mods` folder:
   * **Windows:** `%APPDATA%\.minecraft\mods`
   * **macOS:** `~/Library/Application Support/minecraft/mods`
   * **Linux:** `~/.minecraft/mods`
3. Launch Minecraft with the Fabric profile.

## How to talk to villagers

1. **Name a villager:** Rename a name tag in an anvil (for example, `Gareth`) and apply it to a villager.
2. **Talk to it:** Stand within 8 blocks of the villager and type a message containing its name in regular chat:
   > *Hey Gareth, how is the harvest today?*
3. The villager turns towards you, displays `...` while thinking, and pops up its reply in a speech bubble.

## Giving villagers a persona in-game

Players can easily give any villager a custom personality right inside the game using either of two methods:

### Method A: Book & Quill (Survival & Immersive)

1. Write any backstory or personality traits into a **Book and Quill** (or Signed Book). For example:
   > *You are Barnaby, an anxious librarian who hoards enchanted books and is terrified of spiders. Reply in under 12 words.*
2. Hold the book in your main hand and **sneak + right-click** (Shift + Right-Click) on the named villager.
3. The villager reads your book, displays `*Reads book* Understood. I know who I am now.` in a speech bubble, and saves that persona permanently!

### Method B: In-game Command

You can also assign or inspect personas via command without touching any files:

* **Set a persona:**  
  `/aivillagers persona <name> <description>`  
  *(e.g. `/aivillagers persona Gareth You are Gareth, a grumpy blacksmith who loves iron. Reply in under 12 words.`)*  
  If Gareth is nearby, he will turn to you and nod in acknowledgment!
* **View a persona:**  
  `/aivillagers persona <name>` — prints the villager's active persona to your chat.
* **Reset to default:**  
  `/aivillagers resetpersona <name>` — clears the custom persona and restores the default villager prompt.

## Configuration

Settings are stored in `config/aivillagers.json`:

```json
{
  "ollama": {
    "url": "http://localhost:11434/api/chat",
    "model": "qwen2.5:0.5b",
    "num_predict": 20,
    "num_thread": 3,
    "temperature": 0.8,
    "timeout_seconds": 8
  },
  "chat": {
    "radius": 8.0,
    "cooldown_seconds": 10,
    "max_reply_chars": 60,
    "display_seconds": 5,
    "max_queue": 3
  },
  "fallback_lines": [
    "Hmm... give me a moment.",
    "Eh? Say that again.",
    "I'm busy right now."
  ],
  "default_persona": "You are a villager named {name}. You live in a Minecraft village. Reply in under 12 words. Stay in character. Never mention being an AI.",
  "personas": {
    "gareth": "You are Gareth, a grumpy blacksmith villager. Reply in under 12 words."
  }
}
```

| Setting | Default | Description |
| --- | --- | --- |
| `ollama.url` | `"http://localhost:11434/api/chat"` | URL to Ollama chat endpoint (can be a remote server) |
| `ollama.model` | `"qwen2.5:0.5b"` | Model tag in Ollama |
| `ollama.num_predict` | `20` | Max tokens generated (~12-15 words) |
| `ollama.num_thread` | `3` | CPU threads allocated to model inference |
| `ollama.temperature` | `0.8` | Creativity / variability of responses |
| `chat.radius` | `8.0` | Maximum distance (in blocks) to talk to a villager |
| `chat.cooldown_seconds` | `10` | Per-player cooldown between dialogues |
| `chat.max_reply_chars` | `60` | Maximum character length of the speech bubble |
| `chat.display_seconds` | `5` | How long the speech bubble stays visible |
| `chat.max_queue` | `3` | Maximum concurrent queries before showing a fallback line |
| `default_persona` | *see above* | Default prompt applied to named villagers (`{name}` is replaced) |
| `personas` | *map* | Per-villager custom personas (keyed by lowercase name) |

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/aivillagers persona <name> <text>` | Everyone | Sets a custom persona for `<name>` and saves to config |
| `/aivillagers persona <name>` | Everyone | Views the current persona for `<name>` |
| `/aivillagers resetpersona <name>` | Everyone | Resets `<name>` to the default persona |
| `/aivillagers status` | Everyone | Shows Ollama connection status, model, and active settings |
| `/aivillagers reload` | Gamemasters / Ops | Reloads `config/aivillagers.json` without restarting the server |
| `/aivillagers test <name> <message>` | Gamemasters / Ops | Tests a dialogue query against Ollama directly in chat |
| `/llmv <...>` | Same | Shorthand alias for `/aivillagers` |

## Building from source

You need Git and JDK 25.

```bash
git clone https://github.com/d-halverson/minecraft-ai-villagers.git
cd minecraft-ai-villagers

# 1. Run unit tests
./gradlew test

# 2. Build mod jar
./gradlew build

# 3. Optional: Run in-game automated self-test server
./gradlew runSelftest
```

The compiled mod jar will be in `build/libs/aivillagers-<version>.jar`.

## Troubleshooting & FAQ

* **The villager doesn't reply:**
  * Check that Ollama is running (`curl http://localhost:11434/api/tags` or `/aivillagers status`).
  * Make sure you are within 8 blocks and typed the villager's exact name in chat.
  * Check server logs for any connection timeout warnings.
* **The replies are too long:**
  * Ensure `num_predict` is set to `20` in `config/aivillagers.json`.
  * Specify "Reply in under 10 words" in the persona.
* **Server TPS drops during generation:**
  * Decrease `ollama.num_thread` from `3` to `2` so Ollama leaves enough CPU cores for Minecraft.
  * Set `OLLAMA_NUM_PARALLEL=1` in your environment.

## Credits and license

* Developed by **Drew Halverson**.
* Built with [Fabric](https://fabricmc.net/) for Minecraft 26.3.
* Powered by [Ollama](https://ollama.com/) and [Qwen2.5](https://huggingface.co/Qwen).
* Licensed under the [MIT License](LICENSE). Minecraft is a trademark of Mojang / Microsoft; this project is not affiliated with or endorsed by Mojang or Microsoft.
