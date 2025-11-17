package golden.botc_mc.botc_mc.game.map;

import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Map discovery/registry. Loads manifests from runtime folder `run/config/plasmid/maps/*` and
 * merges with packaged map index from `data/botc/maps/index.json` when present in the classpath.
 */
public class MapManager {
    private static final Logger LOGGER = LogManager.getLogger("botc.MapManager");

    public static class MapInfo {
        public final String id;
        public final String name;
        public final List<String> authors;
        public final String description;
        public final String nbtFile;

        public MapInfo(String id, String name, List<String> authors, String description, String nbtFile) {
            this.id = id;
            this.name = name;
            this.authors = authors;
            this.description = description;
            this.nbtFile = nbtFile;
        }
    }

    private final Map<String, MapInfo> registry = new HashMap<>();

    public MapManager() {
    }

    public void discoverRuntimeMaps() {
        registry.clear();

        // First, load packaged index (if present) from classpath. Packaged entries are default and
        // may be overridden by runtime manifests with the same id.
        loadPackagedIndex();

        // Then discover runtime maps under run/config/plasmid/maps/*
        Path mapsRoot = Paths.get("run", "config", "plasmid", "maps");
        if (!Files.exists(mapsRoot) || !Files.isDirectory(mapsRoot)) return;

        try {
            try (var stream = Files.list(mapsRoot)) {
                stream.forEach(p -> {
                    try {
                        Path manifest = p.resolve("manifest.json");
                        if (Files.exists(manifest)) {
                            String text = Files.readString(manifest);
                            String idRaw = extractString(text, "id").orElse(null);
                            // normalize id: if no namespace provided, assume 'botc'
                            String id = null;
                            if (idRaw != null) {
                                id = idRaw.contains(":") ? idRaw : ("botc:" + idRaw);
                            }
                            String name = extractString(text, "name").orElse(id);
                            String description = extractString(text, "description").orElse("");
                            List<String> authors = extractStringArray(text, "authors");
                            String nbtFile = extractString(text, "nbt_file").orElse(null);
                            if (id != null) {
                                MapInfo info = new MapInfo(id, name, authors, description, nbtFile);
                                // runtime manifests override packaged entries
                                registry.put(id, info);
                                LOGGER.info("Discovered runtime map: {} -> {}", id, p.toString());
                            } else {
                                LOGGER.warn("Manifest at {} has no 'id' field, skipping", manifest.toString());
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse manifest for map at {}: {}", p.toString(), e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read runtime maps folder: {}", e.getMessage());
        }
    }

    private void loadPackagedIndex() {
        try (InputStream in = MapManager.class.getClassLoader().getResourceAsStream("data/botc/maps/index.json")) {
            if (in == null) return;
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            // find map objects inside the 'maps' array by locating occurrences of '"id"'
            int pos = 0;
            while (true) {
                int idIdx = text.indexOf("\"id\"", pos);
                if (idIdx == -1) break;
                // find object boundaries
                int objStart = text.lastIndexOf('{', idIdx);
                int objEnd = text.indexOf('}', idIdx);
                if (objStart == -1 || objEnd == -1 || objEnd <= objStart) {
                    pos = idIdx + 4;
                    continue;
                }
                String obj = text.substring(objStart, objEnd + 1);
                String id = extractString(obj, "id").orElse(null);
                String name = extractString(obj, "name").orElse(id);
                String description = extractString(obj, "description").orElse("");
                List<String> authors = extractStringArray(obj, "authors");
                String nbtFile = extractString(obj, "nbt_file").orElse(null);

                // normalize packaged index id similarly: assume 'botc' namespace when missing
                String idNorm = id != null && id.contains(":") ? id : (id != null ? "botc:" + id : null);
                String nameNorm = name != null ? name : idNorm;
                MapInfo info = new MapInfo(idNorm, nameNorm, authors, description, nbtFile);
                if (idNorm != null) {
                    registry.putIfAbsent(idNorm, info);
                    LOGGER.info("Loaded packaged map index entry: {}", idNorm);
                }
                pos = objEnd + 1;
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read packaged map index: {}", e.getMessage());
        }
    }

    public Optional<Identifier> getById(String id) {
        if (registry.containsKey(id)) {
            try {
                return Optional.of(Identifier.of(id));
            } catch (Exception ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public Optional<MapInfo> getInfo(String id) {
        return Optional.ofNullable(registry.get(id));
    }

    public List<String> listIds() {
        return new ArrayList<>(registry.keySet());
    }

    public List<MapInfo> listInfos() {
        return new ArrayList<>(registry.values());
    }

    // Helper: naive string field extraction for small manifests
    private static Optional<String> extractString(String json, String key) {
        String qKey = '"' + key + '"';
        int idx = json.indexOf(qKey);
        if (idx == -1) return Optional.empty();
        int colon = json.indexOf(':', idx);
        if (colon == -1) return Optional.empty();
        int start = json.indexOf('"', colon + 1);
        int end = json.indexOf('"', start + 1);
        if (start == -1 || end == -1) return Optional.empty();

        String raw = json.substring(start + 1, end);
        return Optional.of(raw);
    }

    private static List<String> extractStringArray(String json, String key) {
        List<String> out = new ArrayList<>();
        String qKey = '"' + key + '"';
        int idx = json.indexOf(qKey);
        if (idx == -1) return out;
        int colon = json.indexOf(':', idx);
        if (colon == -1) return out;
        int start = json.indexOf('[', colon + 1);
        if (start == -1) return out;
        int end = json.indexOf(']', start + 1);
        if (end == -1) return out;
        String inner = json.substring(start + 1, end);
        // split on commas, extract quoted strings
        int i = 0;
        while (i < inner.length()) {
            int q1 = inner.indexOf('"', i);
            if (q1 == -1) break;
            int q2 = inner.indexOf('"', q1 + 1);
            if (q2 == -1) break;
            out.add(inner.substring(q1 + 1, q2));
            i = q2 + 1;
            int comma = inner.indexOf(',', i);
            if (comma == -1) break;
            i = comma + 1;
        }
        return out;
    }
}
