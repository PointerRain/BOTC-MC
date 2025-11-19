# Adding External Maps to BOTC

This document explains how to add maps to BOTC using **map templates**, either
bundled inside the mod JAR or provided as separate datapacks.

At runtime, BOTC discovers map templates via Minecraft's normal data pack
mechanism. There is no longer a separate `run/config/plasmid/maps` manifest
system.

---

## 1. How maps are identified

Maps are stored as NBT structure files under a `map_template` folder in a data
pack and are referenced by a standard Minecraft identifier:

- **File location** (inside a data pack or the mod JAR):
  - `data/<namespace>/map_template/<name>.nbt`
- **Map ID / resource location** used in configs and code:
  - `<namespace>:map_template/<name>`

For the BOTC mod itself, the namespace is typically:

- `botc-mc`

Examples:

- File: `data/botc-mc/map_template/test.nbt` → ID: `botc-mc:map_template/test`
- File: `data/botc-mc/map_template/testv2.nbt` → ID: `botc-mc:map_template/testv2`

The internal loader (`MapTemplateWrapper` and related code) also supports some
shorthand forms, but using the full `namespace:map_template/name` id is the most
reliable.

---

## 2. Bundling maps inside the mod JAR

To ship a map **with the mod JAR**, place the NBT file under
`src/main/resources` in the correct data-pack layout.

### 2.1. Directory layout

In the source tree:

```text
src/
  main/
    resources/
      data/
        botc-mc/
          map_template/
            test.nbt
            testv2.nbt
          plasmid/
            game/
              botc-mc.json
              testv2.json
      fabric.mod.json
```

The important part for maps is:

- `src/main/resources/data/botc-mc/map_template/<name>.nbt`

When you run `./gradlew build`, these files are packaged into the JAR at the
same paths (`data/botc-mc/map_template/...`), so the server can load them as
normal data-pack resources.

### 2.2. Referencing a bundled map in Plasmid game JSON

Plasmid game definitions live under:

- `data/botc-mc/plasmid/game/*.json`

Each game definition can point to a specific map id. A minimal example:

```json
{
  "type": "botc-mc:game_type_id",
  "map": "botc-mc:map_template/testv2",
  "settings": {
    "players": 8,
    "time_limit": 300
  }
}
```

Here:

- The map template NBT must exist at
  `data/botc-mc/map_template/testv2.nbt` (inside the JAR).
- The game config refers to it as `botc-mc:map_template/testv2`.

### 2.3. Opening a bundled map in-game

Once the JAR is on the server and loaded:

1. Start the dev server (for example via `./gradlew runServer`).
2. In-game (as an operator), run:

   ```mcfunction
   /game open botc-mc:testv2
   ```

   or, to be explicit about the full map id in the game config, ensure the
   relevant `plasmid/game/*.json` points at `botc-mc:map_template/testv2`.

The BOTC game logic will load the map template via `MapTemplateWrapper`, build a
world using `TemplateChunkGenerator`, and then apply its own spawn/region
handling.

---

## 3. Shipping maps as an external datapack

Instead of bundling map templates into the mod JAR, you can provide them via a
standard Minecraft datapack. This is a good approach for community or
server-specific maps.

### 3.1. Datapack structure

Create a datapack folder under your world's `datapacks` directory, e.g.:

```text
<world>/datapacks/botc-mc-extra-maps/
  pack.mcmeta
  data/
    botc-mc/
      map_template/
        custom_map_1.nbt
        custom_map_2.nbt
      plasmid/
        game/
          custom_map_1.json
          custom_map_2.json
```

Example `pack.mcmeta` (adjust `pack_format` for your Minecraft version):

```json
{
  "pack": {
    "pack_format": 48,
    "description": "Extra BOTC maps"
  }
}
```

### 3.2. Map ids in a datapack

If you place `custom_map_1.nbt` at:

- `data/botc-mc/map_template/custom_map_1.nbt`

then the corresponding map id is:

- `botc-mc:map_template/custom_map_1`

Your Plasmid game JSON in the datapack should reference this id, for example:

```json
{
  "type": "botc-mc:game_type_id",
  "map": "botc-mc:map_template/custom_map_1",
  "settings": {
    "players": 10
  }
}
```

Place this JSON at:

- `data/botc-mc/plasmid/game/custom_map_1.json`

Once the datapack is enabled, Plasmid will see this game definition and BOTC
can open it.

### 3.3. Enabling the datapack and opening the game

1. Copy the datapack folder to your world:

   - Dev world: `run/world/datapacks/botc-mc-extra-maps/`
   - Dedicated server world: `<server-world>/datapacks/botc-mc-extra-maps/`

2. Start or restart the server.
3. Verify and enable the datapack if needed:

   ```mcfunction
   /datapack list
   /datapack enable "file/botc-mc-extra-maps"
   ```

4. Open the game using the game id defined by your JSON, e.g.:

   ```mcfunction
   /game open botc-mc:custom_map_1
   ```

As long as the game definition's `map` field points at a valid
`botc-mc:map_template/<name>` and the NBT exists under `data/botc-mc/map_template/`,
BOTC will load it.

---

## 4. Map metadata and versioning

`MapTemplateWrapper` enforces a simple format version via a `track_format`
integer stored in the template's metadata.

During construction, it does:

```java
int mapFormat = template.getMetadata()
        .getData()
        .getInt("track_format", 0);

if (mapFormat < CURRENT_MAP_FORMAT) {
    throw new GameOpenException(Text.of("This map was built for an earlier version of the mod."));
} else if (mapFormat > CURRENT_MAP_FORMAT) {
    throw new GameOpenException(Text.of("This map was built for a future version of the mod."));
}
```

Where `CURRENT_MAP_FORMAT` is currently `1`.

To avoid version errors:

- Ensure your exported map templates include a `track_format` metadata field set
  to the correct value (currently `1`).
- If you intentionally change the map format later, update
  `CURRENT_MAP_FORMAT` and re-export your maps.

If you see in-game errors like:

> This map was built for an earlier version of the mod.

or

> This map was built for a future version of the mod.

then the map's `track_format` does not match `CURRENT_MAP_FORMAT` and you may
need to re-export or adjust the metadata.

---

## 5. Quick reference

- **Bundled (JAR) map**
  - NBT: `src/main/resources/data/botc-mc/map_template/<name>.nbt`
  - Map id: `botc-mc:map_template/<name>`
  - Game JSON: `src/main/resources/data/botc-mc/plasmid/game/<game-id>.json`
  - Open: `/game open botc-mc:<game-id>`

- **External datapack map**
  - NBT: `<world>/datapacks/<pack>/data/botc-mc/map_template/<name>.nbt`
  - Map id: `botc-mc:map_template/<name>`
  - Game JSON: `<world>/datapacks/<pack>/data/botc-mc/plasmid/game/<game-id>.json`
  - Open: `/game open botc-mc:<game-id>`

This reflects the current map loading system in the code (`MapTemplateWrapper`,
`Map`, and Plasmid game definitions) and replaces the old
`run/config/plasmid/maps` manifest-based approach.
