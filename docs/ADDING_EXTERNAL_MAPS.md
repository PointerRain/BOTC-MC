How to Add External Maps (runtime, no JAR rebuild)

This document explains how to add maps to BOTC without building them into the mod JAR.

Overview
- BOTC discovers maps from two places:
  1) Packaged index (compiled into the mod): src/main/resources/data/botc/maps/index.json.
  2) Runtime manifests in run/config/plasmid/maps/<id>/manifest.json — these are discovered while the server is running.

Recommended runtime layout
- Create a folder per map inside run/config/plasmid/maps/ using a simple name (for example: example):
  run/config/plasmid/maps/example/

- Place your exported structure (NBT) file in the same folder and name it anything, for example `test.nbt`.

- Create a manifest.json in the same folder with this shape:
  {
    "id": "botc:example",
    "name": "My External Map",
    "authors": ["you"],
    "description": "Short description of the map",
    "nbt_file": "test.nbt"
  }

Notes about the `id` field
- Use a namespaced ID: `botc:example` is preferred. If you only put `example` it will be normalized to `botc:example`.
- The loader also supports `botc:map_template/<name>` style identifiers for packaged templates. For runtime manifests, prefer `botc:<name>`.

How to add the map (step-by-step)
1) Copy your `.nbt` structure file into `run/config/plasmid/maps/<name>/` (example: `run/config/plasmid/maps/example/test.nbt`).
2) Create `run/config/plasmid/maps/<name>/manifest.json` using the sample above and referencing the `nbt_file` (relative path inside that folder).
3) In-game, run `/botc map list` to see the discovered maps. If the server is running when you add the map, re-run `/botc map list` (the MapManager rescans the folder on demand).
4) To select the map: `/botc map set example` or `/botc map set botc:example`.
5) Start the BOTC game normally; the waiting code will attempt to load the map. If the map is a runtime NBT the mod will try to locate it using the manifest's `nbt_file` and apply it as a template.

Troubleshooting
- If `/botc map list` doesn't show your map:
  - Ensure `manifest.json` is valid JSON and contains the `id` key.
  - Ensure `nbt_file` points to a file that actually exists in the same folder.
  - Check the server logs for MapManager messages: it logs discovered runtime maps and any manifest parsing errors.

- If the game fails to load the selected map and the log says it tried `minecraft:<name>`:
  - Ensure your `id` is namespaced (e.g., `botc:example`). The mod normalizes unqualified ids to `botc:<name>`, but older persisted settings or manual edits can store unqualified ids that may be interpreted differently. See `run/config/botc.properties` and set `mapId=botc:example` manually if needed.

Advanced
- Packaged maps: if you prefer to ship the map inside the mod JAR you must place the NBT in `src/main/resources/data/botc/map_template/<name>.nbt` and add an entry to `src/main/resources/data/botc/maps/index.json` with `id": "botc:<name>"` and `nbt_file` pointing to the file name.
- If you want automatic runtime reloading without restarting other services, place the map in `run/config/plasmid/maps/<name>/` and use the manifest described above. BOTC currently scans the folder when `MapManager.discoverRuntimeMaps()` is called (commands and game-open paths call this). I can add a filewatcher to auto-detect added maps on disk if you'd like.

If you'd like, I can also:
- Add a small CLI or utility task to validate manifests and `.nbt` presence.
- Implement an in-game uploader command that accepts a structure file from a player and stores it in the runtime folder.

