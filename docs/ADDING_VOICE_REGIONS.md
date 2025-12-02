# Voice Chat Regions in BOTC

This document describes how BOTC integrates with Simple Voice Chat, how
**voice regions** work on a per-map basis, how configuration is stored, and the
commands available to create and manage those regions.

The goal is to keep this focused on behaviour that is guaranteed by the current
code: regions are stored per map, persisted in JSON, and synchronized with
Simple Voice Chat groups when the server and maps load.

---

## 1. Overview

BOTC uses **regions** inside a map to control which Simple Voice Chat group a
player should be in at any given time. The important pieces are:

- A **map** (for example `botc-mc:map1`) is loaded from a map template.
- For each map, there is an associated **per-map JSON** config that contains:
  - Voice **groups** (names and settings).
  - Voice **regions** (3D areas in the world linked to a group name).
- At runtime, BOTC:
  - Loads the map and its per-map JSON.
  - Creates or repairs Simple Voice Chat groups for the configured groups.
  - Starts a region tracking task that moves players in and out of groups
    based on their position.

All of this is implemented under `golden.botc_mc.botc_mc.game.voice`.

---

## 2. Where voice region data is stored

Voice region data is stored **per map**, alongside the main map configuration.

- For each game/map id (for example `botc-mc:map1`), BOTC expects/maintains a
  JSON document that includes voice configuration.
- The map JSON contains (among other things):
  - A `voice_groups` section: definitions of the groups that should exist.
  - A `voice_regions` section: a list of regions, each mapped to a group name.

Because this data lives in the same JSON as the map config, you can:

- Ship default voice groups/regions **in a datapack** with the map.
- Let admins modify and save voice regions via commands, and BOTC will write
  the updates back to that JSON so they persist across restarts.

> Minimum guarantee: if a map JSON includes valid `voice_groups` and
> `voice_regions`, BOTC will load them on map open and keep them in sync with
> Simple Voice Chat while the game is running.

---

## 3. Simple Voice Chat integration

Integration with Simple Voice Chat is handled by the `SvcBridge` and
`BotcVoicechatPlugin` classes. In broad terms, they do the following:

- Detect whether the Simple Voice Chat mod is present and log one of:
  - Not detected → voice integration disabled.
  - Detected, wrong version → voice integration disabled (logged reason).
  - Detected, supported version → integration enabled.
- Provide reflection-based helpers to:
  - Create and remove groups.
  - Move players into and out of groups.
- Initialize/groups on server start:
  - When the server starts, `BotcVoicechatPlugin` preloads any voice groups and
    regions defined in the active map JSON(s).
  - It attempts to create the configured groups in Simple Voice Chat and
    remembers their IDs.

If Simple Voice Chat is not present, all region logic is effectively a no-op
and the server runs normally without voice features.

---

## 4. How regions work at runtime

Runtime behaviour is coordinated by three main components:

- `VoiceRegionService` – high-level service that knows which map/world is
  currently active and provides access to the underlying managers.
- `VoiceRegionManager` – owns the **per-map set of regions**, handles
  load/save to JSON, and answers questions like "which region is this player
  in?".
- `VoiceRegionTask` – a periodic task that:
  - Checks each tracked player’s position.
  - Figures out which region they are in (if any).
  - Asks `SvcBridge` to move them into the correct group, or leave groups
    when they exit regions.

Important guarantees:

- Regions are evaluated **per map/world**; when the game for that map is
  opened, region tracking is tied to that map’s game space/world.
- When a player enters a region whose group exists, BOTC will request a group
  join via Simple Voice Chat.
- When a player leaves all regions, BOTC will request that they leave the
  managed group(s).
- Behaviour falls back gracefully when voice is disabled or groups are not
  available; the commands and JSON simply do not affect voice in that case.

---

## 5. Voice commands

All voice-related commands are provided as subcommands of `/botc voice …`.
Only operators (or users with the relevant permission if you use a permissions
mod) should be able to run them.

The exact set of commands may evolve, but the following behaviour is guaranteed
by the current implementation.

### 5.1. `/botc voice debug on|off`

Toggle **voice region debug logs**.

- `on` – enables detailed logging for:
  - Region entry/exit detection.
  - Group join/leave attempts.
  - Any errors or suppressed attempts.
- `off` – disables this extra logging.

Use this when setting up or troubleshooting regions; turn it off once things
are working to keep logs clean.

### 5.2. `/botc voice group add <name>`

Create or register a **voice group** for the current map.

- `<name>` – the logical group name, for example `Church`.
- Behaviour:
  - Adds/updates the group entry in the current map’s JSON.
  - Attempts to create (or repair) a corresponding Simple Voice Chat group
    using reflection via `SvcBridge`.
  - Logs if creation failed or had to be deduplicated, but the JSON will still
    contain the desired group definition.

After creating a group, you can associate a region with it so players are moved
into it automatically.

### 5.3. `/botc voice group remove <name>`

Remove a voice group from the current map’s configuration.

- `<name>` – the logical group name to remove.
- Behaviour:
  - Removes the group entry from the map’s JSON.
  - Requests that Simple Voice Chat remove the backing group where possible.
  - Clears any regions that referenced this group, or leaves them effectively
    inactive (implementation detail).

### 5.4. `/botc voice region add <name>`

Create a new **voice region** in the current map, bound to a group.

- Typical usage pattern:
  1. Stand at one corner of the desired region and run a command (or use a
     selection method implemented by the mod) to mark a first position.
  2. Stand at the opposite corner and run a complementary command to mark the
     second position.
  3. Run `/botc voice region add <name>` with `<name>` equal to the group name
     you want this region to connect to.

- Behaviour (as implemented by `VoiceRegionCommands` + `VoiceRegionManager`):
  - Captures a 3D bounding box (min/max x, y, z) in the active map/world.
  - Stores it in the map’s JSON under `voice_regions`, linked to the given
    group name.
  - Persists the updated JSON, so the region is restored on restart.

Once a region is added and the corresponding group exists, `VoiceRegionTask`
will start moving players into and out of that group based on their position.

### 5.5. `/botc voice region remove <id|name>`

Remove an existing region from the current map.

- You can reference the region by its internal id or by the associated group name.
- Removes the region entry from the map JSON and stops tracking it at runtime.

### 5.6. `/botc voice region list`

List currently configured voice regions for the active map.

- Shows at least:
  - The region id/index.
  - The associated group name.
  - The bounding box coordinates.
- Useful for verifying that your edits took effect and for finding ids to
  remove.

---

## 6. Persistence and per-map behaviour

Key points about persistence and map scoping:

- Voice configuration is **per map id**.
  - Different maps can have completely different groups and regions.
- Configuration is stored in the **same JSON** that defines the map’s other
  options, so:
  - You can ship defaults with the map (in a datapack).
  - Server admins can further customize via commands.
- On server start and when a map is opened:
  - BOTC loads the map JSON, including `voice_groups` and `voice_regions`.
  - `BotcVoicechatPlugin` and `SvcBridge` ensure that matching Simple Voice
    Chat groups exist.
  - `VoiceRegionTask` begins tracking players in the game’s world.
- On server stop or map close:
  - The current configuration is written back to JSON if commands have changed
    it.

Because the data format is plain JSON, you can copy the map’s config between
servers to reuse the same voice region layout elsewhere.

---

## 7. Minimal setup example

This flow shows adding a voice region for a single building (the **Church**). You can repeat the same steps for other buildings later.

1. Ensure Simple Voice Chat is installed and recognized.
   - Look for a log line similar to:
     - `[botc-mc] [Voice] Simple Voice Chat integration enabled (version=...).`
2. Open the BOTC game/map you want to configure:

   ```mcfunction
   /game open botc-mc:map1
   ```

3. Create a voice group for the Church:

   ```mcfunction
   /botc voice group add Church
   ```

4. Define the Church region:
   - Select first corner → select opposite corner (using the mod’s selection commands).
   - Add the region:

   ```mcfunction
   /botc voice region add Church
   ```

5. Enable debug temporarily to verify behaviour:

   ```mcfunction
   /botc voice debug on
   ```

6. Walk into and out of the Church and watch the server log for:
   - Region enter/exit messages.
   - Group join/leave attempts for `Church`.

7. Disable debug when satisfied:

   ```mcfunction
   /botc voice debug off
   ```

From now on, when this map is opened BOTC will:

- Ensure the `Church` group exists in Simple Voice Chat.
- Track players in the Church region and move them into/out of the `Church` voice group automatically.
- Persist changes made via `/botc voice` commands in the per-map JSON.

> To add more buildings later (e.g. Tavern, Inn), repeat steps 3–4 with a new group name and region.
