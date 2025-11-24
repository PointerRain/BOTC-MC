# Adding External Maps to BOTC

This document explains the **minimum** you need to do for BOTC to see and open
maps via Plasmid. It focuses only on behaviour that is guaranteed by the
current code: file layout, IDs, and the optional `map_format` version check.

At runtime, BOTC discovers map templates via Minecraft's normal data pack
mechanism. There is no longer a separate `run/config/plasmid/maps` manifest
system.

> Required steps:
> 1. Place your NBT file in `data/.../map_template/`.
> 2. Point a Plasmid game JSON at it by resource ID.
> 3. (Optional) Provide a `map_format` integer in the template metadata for versioning.

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
reliable and is the only form this document relies on.

---

## 2. Bundling maps inside the mod JAR

To ship a map **with the mod JAR**, place the NBT file under
`src/main/resources` in the correct data-pack layout.

### 2.1. Directory layout

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
  "map": "botc-mc:map_template/testv2"
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

   This opens the Plasmid game whose JSON id is `botc-mc:testv2`, which in turn
   points at the map template id `botc-mc:map_template/testv2`.

As long as the file and id are correct, BOTC will ask
`MapTemplateSerializer.loadFromResource` for that id and generate the game
world from the template.

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
  "map": "botc-mc:map_template/custom_map_1"
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

## 4. Map metadata and versioning (minimum required)

BOTC can read an optional format version field from map template metadata.
`MapTemplateWrapper` looks for an integer field named `map_format`. If present,
it is compared against an internal `CURRENT_MAP_FORMAT` constant and a warning
is logged when they differ. These warnings do **not** prevent the map from
loading.

This field is **not required**; if you omit `map_format`, BOTC treats the map
as format `0` and still attempts to load it. Supplying `map_format` helps with
future compatibility but is optional.

---

## 5. Quick reference

- **Bundled (JAR) map)**
  - NBT: `src/main/resources/data/botc-mc/map_template/<name>.nbt`
  - Map id: `botc-mc:map_template/<name>`
  - Game JSON: `src/main/resources/data/botc-mc/plasmid/game/<game-id>.json`
  - Open: `/game open botc-mc:<game-id>`

- **External datapack map**
  - NBT: `<world>/datapacks/<pack>/data/botc-mc/map_template/<name>.nbt`
  - Map id: `botc-mc:map_template/<name>`
  - Game JSON: `<world>/datapacks/<pack>/data/botc-mc/plasmid/game/<game-id>.json`
  - Open: `/game open botc-mc:<game-id>`

That is the guaranteed behaviour today: if the files are in the right place
and referenced by the right id, BOTC will be able to open the map via Plasmid.

---

## 6. Minimal step-by-step example

This example shows the smallest working setup for a custom map called
`town_square`.

### 6.1. Export the map template

1. Build your map in a normal Minecraft world.
2. Use your map-template export tool to save the build area as
   `town_square.nbt`.
3. (Optional) Add a `map_format` integer in the template metadata, for example:
   - `map_format = 1`.

### 6.3. Enable the datapack and open the game

The map will load if all of the following are true:

- `town_square.nbt` is at `data/mymaps/map_template/town_square.nbt`.
- `town_square.json` is at `data/mymaps/plasmid/game/town_square.json`.
- `config.map_id` is exactly `"mymaps:map_template/town_square"`.

In that case, BOTC will load the map and generate a game world from it. Any
additional behaviour (spawn logic, regions, layout, voice regions, etc.) is
implemented by higher-level systems and may evolve in future versions, but this
minimal contract remains stable.

